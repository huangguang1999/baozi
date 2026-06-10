#!/usr/bin/env node

import { spawn } from "node:child_process";
import { readFile } from "node:fs/promises";

const DEFAULT_APP = "/Applications/Codex.app";
const DEFAULT_PORT = 9333;
const DEFAULT_HOST_ID = "local";
const DEFAULT_USER_DATA_DIR = "/tmp/codex-desktop-controller-profile";
const TARGET_URL_PREFIX = "app://-/index.html";

async function main() {
  const parsed = parseArgs(process.argv.slice(2));
  if (parsed.help || !parsed.command) {
    printHelp();
    process.exit(parsed.help ? 0 : 64);
  }

  if (parsed.command === "launch") {
    const target = await ensureDebuggerTarget(parsed.options, {
      allowLaunch: true,
      forceLaunch: parsed.options.freshLaunch,
    });
    if (parsed.options.projectPath) {
      await openProjectPath(parsed.options.app, parsed.options.projectPath);
    }
    console.log(JSON.stringify(debuggerInfo(parsed.options, target), null, 2));
    return;
  }

  if (parsed.command === "open-project") {
    await ensureDebuggerTarget(parsed.options, {
      allowLaunch: parsed.options.launch,
      forceLaunch: parsed.options.freshLaunch,
    });
    if (!parsed.options.projectPath) {
      throw new Error("Missing --project-path for open-project");
    }
    await openProjectPath(parsed.options.app, parsed.options.projectPath);
    console.log("ok");
    return;
  }

  const target = await ensureDebuggerTarget(parsed.options, {
    allowLaunch: parsed.options.launch,
    forceLaunch: parsed.options.freshLaunch,
  });
  const controller = new CodexDesktopController(target.webSocketDebuggerUrl, parsed.options);

  switch (parsed.command) {
    case "snapshot": {
      const snapshot = await controller.snapshot();
      console.log(JSON.stringify(snapshot, null, 2));
      return;
    }
    case "list-projects": {
      const projects = await controller.listProjects();
      console.log(JSON.stringify(projects, null, 2));
      return;
    }
    case "new-thread": {
      requireOption(parsed.options.project, "--project");
      await controller.newThread(parsed.options.project);
      console.log("ok");
      return;
    }
    case "send-message": {
      const message = await resolveMessage(parsed.options);
      await controller.sendMessage(message);
      console.log("ok");
      return;
    }
    case "new-thread-and-send": {
      requireOption(parsed.options.project, "--project");
      const message = await resolveMessage(parsed.options);
      await controller.newThread(parsed.options.project);
      await controller.sendMessage(message);
      console.log("ok");
      return;
    }
    case "open-thread": {
      requireOption(parsed.options.threadTitle, "--thread-title");
      await controller.openThread(parsed.options.threadTitle);
      console.log("ok");
      return;
    }
    case "thread-state": {
      const state = await controller.threadState();
      console.log(JSON.stringify(state, null, 2));
      return;
    }
    case "wait-for-idle": {
      const state = await controller.waitForIdle();
      console.log(JSON.stringify(state, null, 2));
      return;
    }
    case "run-turn": {
      const message = await resolveMessage(parsed.options);
      const state = await controller.runTurn({
        projectName: parsed.options.project,
        message,
      });
      console.log(JSON.stringify(state, null, 2));
      return;
    }
    default:
      throw new Error(`Unsupported command: ${parsed.command}`);
  }
}

class CodexDesktopController {
  constructor(webSocketDebuggerUrl, options) {
    this.webSocketDebuggerUrl = webSocketDebuggerUrl;
    this.options = options;
  }

  async snapshot() {
    return this.runPageAction(`
      return snapshot();
    `);
  }

  async listProjects() {
    return this.runPageAction(`
      return listProjects();
    `);
  }

  async threadState() {
    return this.runPageAction(`
      return threadState();
    `);
  }

  async newThread(projectName) {
    const result = await this.runPageAction(
      `
        const clicked = clickButtonByAria("Start new thread in " + input.projectName);
        return { clicked, snapshot: snapshot() };
      `,
      { projectName },
    );

    if (!result.clicked) {
      throw new Error(`Could not find project button for "${projectName}"`);
    }

    await this.waitFor(
      "new thread composer",
      `
        const snap = snapshot();
        return snap.bodyText.includes("New thread") && snap.hasComposer;
      `,
    );
  }

  async sendMessage(message) {
    const baseline = await this.snapshot();
    const baselineOccurrences = countOccurrences(baseline.bodyText, message);
    const composerResult = await this.runPageAction(
      `
        return { composerState: setComposerText(input.message), snapshot: snapshot() };
      `,
      { message },
    );

    if (!composerResult.composerState?.ok) {
      throw new Error(composerResult.composerState?.reason ?? "Failed to set composer text");
    }

    await this.waitFor(
      "composer text to settle",
      `
        return snapshot().composerText.trim() === input.message;
      `,
      { message },
      3000,
      100,
    );

    await sleep(100);

    const submitResult = await this.runPageAction(`
      return { submitState: clickComposerSubmit(), snapshot: snapshot() };
    `);

    if (!submitResult.submitState?.ok) {
      throw new Error(submitResult.submitState?.reason ?? "Failed to submit composer");
    }

    await this.waitFor(
      "submitted message to render",
      `
        const snap = snapshot();
        return !snap.composerText.trim() &&
          countOccurrences(snap.bodyText, input.message) > input.baselineOccurrences;
      `,
      { message, baselineOccurrences },
      this.options.timeoutMs,
      250,
    );
  }

  async openThread(threadTitle) {
    const result = await this.runPageAction(
      `
        const clicked = clickButtonByText(input.threadTitle);
        return { clicked, snapshot: snapshot() };
      `,
      { threadTitle },
    );

    if (!result.clicked) {
      throw new Error(`Could not find thread titled "${threadTitle}"`);
    }

    await this.waitFor(
      `thread "${threadTitle}"`,
      `
        return snapshot().bodyText.includes(input.threadTitle);
      `,
      { threadTitle },
    );
  }

  async waitForIdle() {
    await this.waitFor(
      "thread to become idle",
      `
        const state = threadState();
        return Boolean(state.hasConversation) && !state.isBusy;
      `,
      {},
      this.options.timeoutMs,
      400,
    );

    await sleep(300);
    return this.threadState();
  }

  async runTurn({ projectName, message }) {
    if (projectName) {
      await this.newThread(projectName);
    }

    await this.sendMessage(message);
    return this.waitForIdle();
  }

  async waitFor(label, predicateBody, input = {}, timeoutMs = this.options.timeoutMs, intervalMs = 200) {
    const startedAt = Date.now();
    while (Date.now() - startedAt < timeoutMs) {
      const matched = await this.runPageAction(`
        return Boolean(${wrapBody(predicateBody)});
      `, input);
      if (matched) {
        return;
      }
      await sleep(intervalMs);
    }
    throw new Error(`Timed out waiting for ${label}`);
  }

  async runPageAction(body, input = {}) {
    const expression = buildPageExpression(body, input);
    return evaluateSerializable(this.webSocketDebuggerUrl, expression);
  }
}

function buildPageExpression(body, input) {
  return `
    (async () => {
      const input = ${JSON.stringify(input)};

      function textOf(node) {
        return (node?.innerText || node?.textContent || "").trim();
      }

      function countOccurrences(haystack, needle) {
        if (!needle) {
          return 0;
        }

        let count = 0;
        let startIndex = 0;
        while (true) {
          const matchIndex = haystack.indexOf(needle, startIndex);
          if (matchIndex === -1) {
            return count;
          }
          count += 1;
          startIndex = matchIndex + needle.length;
        }
      }

      function allButtons() {
        return Array.from(document.querySelectorAll("button"));
      }

      function normalizeText(text) {
        return String(text ?? "")
          .replace(/\\u00a0/g, " ")
          .split("\\n")
          .map((line) => line.trimEnd())
          .join("\\n")
          .trim();
      }

      function clickButtonByAria(label) {
        const button = allButtons().find((element) => element.getAttribute("aria-label") === label);
        if (!button || button.disabled) {
          return false;
        }
        button.click();
        return true;
      }

      function clickButtonByText(label) {
        const button = allButtons().find((element) => textOf(element) === label);
        if (!button || button.disabled) {
          return false;
        }
        button.click();
        return true;
      }

      function composer() {
        return document.querySelector(".ProseMirror");
      }

      function conversationRoot() {
        return document.querySelector('[data-thread-find-target="conversation"]');
      }

      function hasAncestorMatching(node, selector, stopAt) {
        let current = node.parentElement;
        while (current && current !== stopAt) {
          if (current.matches(selector)) {
            return true;
          }
          current = current.parentElement;
        }
        return false;
      }

      function compareDocumentOrder(left, right) {
        if (left === right) {
          return 0;
        }
        const position = left.compareDocumentPosition(right);
        if (position & Node.DOCUMENT_POSITION_FOLLOWING) {
          return -1;
        }
        if (position & Node.DOCUMENT_POSITION_PRECEDING) {
          return 1;
        }
        return 0;
      }

      function activeThreadMeta() {
        const header = document.querySelector("button[aria-label='Thread actions']")?.closest(".draggable");
        if (header) {
          const title = normalizeText(
            header.querySelector("span.no-drag.truncate")?.textContent ?? "",
          );
          const projectName = normalizeText(
            header.querySelector("button.no-drag.max-w-full.truncate")?.textContent ?? "",
          );

          if (title || projectName) {
            return {
              projectName: projectName || null,
              threadTitle: title || null,
            };
          }
        }

        const activeRow = Array.from(document.querySelectorAll('[role="button"]')).find((element) => {
          return !element.closest('[data-thread-find-target="conversation"]') &&
            String(element.className || "").includes("bg-token-list-hover-background");
        });

        if (!activeRow) {
          return {
            projectName: null,
            threadTitle: null,
          };
        }

        const lines = normalizeText(activeRow.innerText).split("\\n").filter(Boolean);
        const folder = activeRow.closest('[role="listitem"][aria]');
        return {
          projectName: folder?.getAttribute("aria") ?? null,
          threadTitle: lines[0] ?? null,
        };
      }

      function conversationMessages() {
        const root = conversationRoot();
        if (!root) {
          return [];
        }

        const selectors = {
          user: "div.group.flex.w-full.flex-col.items-end.justify-end.gap-1",
          assistant: "div.group.flex.min-w-0.flex-col",
        };

        const candidates = [
          ...Array.from(root.querySelectorAll(selectors.user))
            .filter((node) => !hasAncestorMatching(node, selectors.user, root))
            .map((node) => ({ role: "user", node })),
          ...Array.from(root.querySelectorAll(selectors.assistant))
            .filter((node) => !hasAncestorMatching(node, selectors.assistant, root))
            .map((node) => ({ role: "assistant", node })),
        ].sort((left, right) => compareDocumentOrder(left.node, right.node));

        return candidates
          .map(({ role, node }) => ({
            role,
            text: normalizeText(node.innerText),
          }))
          .filter((message) => message.text.length > 0);
      }

      function reactCurrentThreadDetails() {
        const root = document.querySelector("#root");
        const containerKey = root
          ? Object.getOwnPropertyNames(root).find((key) => key.startsWith("__reactContainer$"))
          : null;
        const start = containerKey ? root[containerKey] : null;
        if (!start) {
          return {
            conversationId: null,
            threadKey: null,
            currentGroupId: null,
          };
        }

        const seen = new Set();
        const details = {
          conversationId: null,
          threadKey: null,
          currentGroupId: null,
        };

        function visit(node) {
          if (!node || seen.has(node)) {
            return;
          }
          seen.add(node);

          const props = node.memoizedProps;
          if (props && typeof props === "object") {
            if (details.conversationId == null && typeof props.currentConversationId === "string") {
              details.conversationId = props.currentConversationId;
            }
            if (details.threadKey == null && typeof props.currentThreadKey === "string") {
              details.threadKey = props.currentThreadKey;
            }
            if (details.currentGroupId == null && typeof props.currentGroupId === "string") {
              details.currentGroupId = props.currentGroupId;
            }

            if (
              details.conversationId != null &&
              details.threadKey != null &&
              details.currentGroupId != null
            ) {
              return;
            }
          }

          visit(node.child);
          visit(node.sibling);
        }

        visit(start);
        return details;
      }

      function threadState() {
        const meta = activeThreadMeta();
        const reactThread = reactCurrentThreadDetails();
        const root = conversationRoot();
        const messages = conversationMessages();
        const conversationText = normalizeText(root?.innerText ?? "");
        const body = normalizeText(document.body.innerText);
        const isBusy = conversationText.includes("Thinking") || body.includes("\\nThinking\\n");
        return {
          conversationId: reactThread.conversationId,
          threadKey: reactThread.threadKey,
          currentGroupId: reactThread.currentGroupId,
          projectName: meta.projectName,
          threadTitle: meta.threadTitle,
          hasConversation: Boolean(root),
          isBusy,
          messageCount: messages.length,
          composerText: normalizeText(composer()?.innerText ?? ""),
          messages,
        };
      }

      function composerActionButtons(editor) {
        const editorRect = editor.getBoundingClientRect();
        return allButtons()
          .map((element) => ({
            element,
            rect: element.getBoundingClientRect(),
          }))
          .filter(({ element, rect }) => {
            return !element.disabled &&
              rect.width >= 24 &&
              rect.width <= 40 &&
              rect.height >= 24 &&
              rect.height <= 40 &&
              rect.y >= editorRect.y + 20 &&
              rect.y < editorRect.y + 80 &&
              rect.x > editorRect.x - 20;
          })
          .sort((left, right) => left.rect.x - right.rect.x);
      }

      function focusComposerAtEnd(editor) {
        editor.focus();
        const selection = window.getSelection();
        const range = document.createRange();
        range.selectNodeContents(editor);
        range.collapse(false);
        selection.removeAllRanges();
        selection.addRange(range);
      }

      function clearComposer(editor) {
        focusComposerAtEnd(editor);
        const selection = window.getSelection();
        const range = document.createRange();
        range.selectNodeContents(editor);
        selection.removeAllRanges();
        selection.addRange(range);
        document.execCommand("delete");
      }

      function setComposerText(message) {
        const editor = composer();
        if (!editor) {
          return { ok: false, reason: "composer-not-found" };
        }
        clearComposer(editor);
        focusComposerAtEnd(editor);
        const inserted = document.execCommand("insertText", false, message);
        return {
          ok: true,
          inserted,
          text: editor.innerText,
          html: editor.innerHTML,
        };
      }

      function clickComposerSubmit() {
        const editor = composer();
        if (!editor) {
          return { ok: false, reason: "composer-not-found" };
        }
        const submit = composerActionButtons(editor).at(-1);
        if (!submit) {
          return { ok: false, reason: "submit-button-not-found" };
        }
        submit.element.click();
        return {
          ok: true,
          aria: submit.element.getAttribute("aria-label"),
          className: submit.element.className,
          x: Math.round(submit.rect.x),
          y: Math.round(submit.rect.y),
        };
      }

      function listProjects() {
        return allButtons()
          .map((element) => element.getAttribute("aria-label"))
          .filter((label) => typeof label === "string" && label.startsWith("Project actions for "))
          .map((label) => label.replace("Project actions for ", ""));
      }

      function snapshot() {
        const editor = composer();
        return {
          title: document.title,
          location: window.location.href,
          projects: listProjects(),
          thread: threadState(),
          hasComposer: Boolean(editor),
          composerText: editor ? editor.innerText : "",
          bodyText: document.body.innerText,
          buttons: allButtons().slice(0, 80).map((element) => ({
            text: textOf(element),
            aria: element.getAttribute("aria-label"),
            title: element.getAttribute("title"),
            disabled: element.disabled,
          })),
        };
      }

      ${body}
    })()
  `;
}

function countOccurrences(haystack, needle) {
  if (!needle) {
    return 0;
  }

  let count = 0;
  let startIndex = 0;
  while (true) {
    const matchIndex = haystack.indexOf(needle, startIndex);
    if (matchIndex === -1) {
      return count;
    }
    count += 1;
    startIndex = matchIndex + needle.length;
  }
}

function wrapBody(body) {
  return `(() => { ${body} })()`;
}

async function ensureDebuggerTarget(options, { allowLaunch = false, forceLaunch = false } = {}) {
  let target = await getDebuggerTarget(options.port, options.hostId);
  if (target && !forceLaunch) {
    return target;
  }

  if (!allowLaunch && !forceLaunch) {
    throw new Error(
      `No debugger target is listening on port ${options.port}. Run the "launch" command first or pass --launch.`,
    );
  }

  if (!options.app) {
    throw new Error("Missing --app and no debugger target is listening");
  }

  await launchApp(options);
  target = await waitForDebuggerTarget(options.port, options.hostId, options.timeoutMs);
  return target;
}

function debuggerInfo(options, target = null) {
  return {
    app: options.app,
    port: options.port,
    hostId: options.hostId,
    userDataDir: options.userDataDir,
    debuggerListUrl: `http://127.0.0.1:${options.port}/json/list`,
    webSocketDebuggerUrl: target?.webSocketDebuggerUrl ?? null,
    targetUrl: target?.url ?? null,
  };
}

async function getDebuggerTarget(port, hostId) {
  const response = await fetchJson(`http://127.0.0.1:${port}/json/list`).catch(() => null);
  if (!response) {
    return null;
  }

  const target = response.find((entry) => {
    return entry.type === "page" &&
      typeof entry.url === "string" &&
      entry.url.startsWith(TARGET_URL_PREFIX) &&
      entry.url.includes(`hostId=${hostId}`);
  });

  return target ?? null;
}

async function waitForDebuggerTarget(port, hostId, timeoutMs) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    const target = await getDebuggerTarget(port, hostId);
    if (target) {
      return target;
    }
    await sleep(250);
  }
  throw new Error(`Timed out waiting for debugger target on port ${port}`);
}

async function launchApp(options) {
  const args = [
    "-na",
    options.app,
    "--args",
    `--remote-debugging-port=${options.port}`,
    `--user-data-dir=${options.userDataDir}`,
  ];

  await spawnAndWait("open", args);
  await sleep(1200);
}

async function openProjectPath(appPath, projectPath) {
  await spawnAndWait("open", ["-a", appPath, projectPath]);
  await sleep(1200);
}

async function spawnAndWait(command, args) {
  await new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      stdio: "ignore",
      detached: false,
    });

    child.on("error", reject);
    child.on("exit", (code) => {
      if (code === 0) {
        resolve();
        return;
      }
      reject(new Error(`${command} exited with code ${code}`));
    });
  });
}

async function fetchJson(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} for ${url}`);
  }
  return response.json();
}

async function evaluateSerializable(webSocketDebuggerUrl, expression) {
  const connection = await openCdp(webSocketDebuggerUrl);
  try {
    await connection.send("Runtime.enable");
    const evaluated = await connection.send("Runtime.evaluate", {
      expression,
      returnByValue: false,
      replMode: true,
      userGesture: true,
    });

    if (evaluated.result?.subtype === "promise" && evaluated.result.objectId) {
      const awaited = await connection.send("Runtime.awaitPromise", {
        promiseObjectId: evaluated.result.objectId,
        returnByValue: true,
        generatePreview: true,
      });
      throwIfException(awaited.exceptionDetails);
      return awaited.result?.value;
    }

    throwIfException(evaluated.exceptionDetails);
    return evaluated.result?.value;
  } finally {
    connection.close();
  }
}

function throwIfException(exceptionDetails) {
  if (!exceptionDetails) {
    return;
  }
  const message = exceptionDetails.text ||
    exceptionDetails.exception?.description ||
    "Unknown CDP evaluation error";
  throw new Error(message);
}

async function openCdp(webSocketDebuggerUrl) {
  const socket = new WebSocket(webSocketDebuggerUrl);
  const pending = new Map();
  let nextId = 1;

  socket.addEventListener("message", (event) => {
    const payload = JSON.parse(event.data);
    if (!payload.id || !pending.has(payload.id)) {
      return;
    }

    const { resolve, reject } = pending.get(payload.id);
    pending.delete(payload.id);
    if (payload.error) {
      reject(new Error(payload.error.message));
      return;
    }
    resolve(payload.result);
  });

  await new Promise((resolve, reject) => {
    socket.addEventListener("open", () => resolve(), { once: true });
    socket.addEventListener("error", reject, { once: true });
  });

  return {
    async send(method, params = {}) {
      const id = nextId++;
      socket.send(JSON.stringify({ id, method, params }));
      return new Promise((resolve, reject) => {
        pending.set(id, { resolve, reject });
      });
    },
    close() {
      socket.close();
    },
  };
}

async function resolveMessage(options) {
  if (options.message != null) {
    return options.message;
  }
  if (options.messageFile != null) {
    return readFile(options.messageFile, "utf8");
  }
  throw new Error("Missing --message or --message-file");
}

function parseArgs(argv) {
  const options = {
    app: DEFAULT_APP,
    port: DEFAULT_PORT,
    hostId: DEFAULT_HOST_ID,
    userDataDir: DEFAULT_USER_DATA_DIR,
    project: null,
    projectPath: null,
    message: null,
    messageFile: null,
    threadTitle: null,
    launch: false,
    freshLaunch: false,
    timeoutMs: 15000,
  };

  let command = null;
  let help = false;

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--help" || arg === "-h") {
      help = true;
      continue;
    }

    if (!arg.startsWith("--") && command == null) {
      command = arg;
      continue;
    }

    switch (arg) {
      case "--app":
        options.app = requireValue(argv, ++index, arg);
        break;
      case "--port":
        options.port = Number.parseInt(requireValue(argv, ++index, arg), 10);
        break;
      case "--host-id":
        options.hostId = requireValue(argv, ++index, arg);
        break;
      case "--user-data-dir":
        options.userDataDir = requireValue(argv, ++index, arg);
        break;
      case "--project":
        options.project = requireValue(argv, ++index, arg);
        break;
      case "--project-path":
        options.projectPath = requireValue(argv, ++index, arg);
        break;
      case "--message":
        options.message = requireValue(argv, ++index, arg);
        break;
      case "--message-file":
        options.messageFile = requireValue(argv, ++index, arg);
        break;
      case "--thread-title":
        options.threadTitle = requireValue(argv, ++index, arg);
        break;
      case "--launch":
        options.launch = true;
        break;
      case "--launch-debugger":
        options.launch = true;
        break;
      case "--fresh-launch":
        options.launch = true;
        options.freshLaunch = true;
        break;
      case "--timeout-ms":
        options.timeoutMs = Number.parseInt(requireValue(argv, ++index, arg), 10);
        break;
      default:
        throw new Error(`Unknown argument: ${arg}`);
    }
  }

  return { command, help, options };
}

function requireValue(argv, index, flag) {
  if (index >= argv.length) {
    throw new Error(`Missing value for ${flag}`);
  }
  return argv[index];
}

function requireOption(value, flag) {
  if (!value) {
    throw new Error(`Missing ${flag}`);
  }
}

function printHelp() {
  console.log(`Usage:
  node tools/scripts/codex-desktop-controller.mjs launch [options]
  node tools/scripts/codex-desktop-controller.mjs open-project --project-path <path> [options]
  node tools/scripts/codex-desktop-controller.mjs list-projects [options]
  node tools/scripts/codex-desktop-controller.mjs snapshot [options]
  node tools/scripts/codex-desktop-controller.mjs thread-state [options]
  node tools/scripts/codex-desktop-controller.mjs wait-for-idle [options]
  node tools/scripts/codex-desktop-controller.mjs new-thread --project <sidebar-project> [options]
  node tools/scripts/codex-desktop-controller.mjs send-message --message <text> [options]
  node tools/scripts/codex-desktop-controller.mjs new-thread-and-send --project <sidebar-project> --message <text> [options]
  node tools/scripts/codex-desktop-controller.mjs run-turn [--project <sidebar-project>] --message <text> [options]
  node tools/scripts/codex-desktop-controller.mjs open-thread --thread-title <title> [options]

Common options:
  --app <path>              App bundle to launch or target. Default: ${DEFAULT_APP}
  --port <number>           Remote debugging port. Default: ${DEFAULT_PORT}
  --host-id <id>            Host id in the renderer URL. Default: ${DEFAULT_HOST_ID}
  --user-data-dir <path>    User data dir for launched automation instance.
  --launch                  Launch the app with Electron remote debugging only if the target is missing.
  --launch-debugger         Alias for --launch.
  --fresh-launch            Always start a new app instance instead of reusing the current target.
  --timeout-ms <ms>         Wait timeout for UI transitions. Default: 15000

Command options:
  --project <name>          Sidebar project name such as "codex-test".
  --project-path <path>     Filesystem path to open in Codex.
  --message <text>          Message to send through the composer.
  --message-file <path>     Read the message body from a UTF-8 file.
  --thread-title <title>    Visible thread title to open from the sidebar.

Notes:
  - This controller talks to the Electron renderer over CDP and uses the real in-app
    sidebar/composer controls, not macOS accessibility clicks.
  - \`thread-state\` and \`run-turn\` emit structured JSON so shell scripts can wait for
    a completed turn and capture the visible transcript.
  - \`launch\` and \`--launch\` start a separate app instance via \`open -na ... --args\`
    with \`--remote-debugging-port=<port>\` and \`--user-data-dir=<path>\`.
  - Repeated commands should usually reuse the same debugger target on the same port.
    Use \`--fresh-launch\` only when you intentionally want another clean instance.
  - Example: \`node tools/scripts/codex-desktop-controller.mjs run-turn --launch --project codex-test --message "hi"\`
  - \`open-project\` uses macOS open-file delivery. If you have multiple identical app
    bundles running, prefer a distinct bundle path such as \`Codex copy.app\`.
`);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
});
