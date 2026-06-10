#!/usr/bin/osascript

-- Drives Codex.app through macOS GUI scripting.
--
-- Examples:
--   osascript tools/scripts/codex-app-driver.applescript \
--     --project /Users/sigkitten/dev/codex-test \
--     --new-thread \
--     --message "hello from osascript"
--
--   osascript tools/scripts/codex-app-driver.applescript \
--     --project /Users/sigkitten/dev/codex-test \
--     --message-file /tmp/prompt.txt \
--     --send-mode command-shift-enter

on run argv
	set appName to "Codex"
	set projectPath to missing value
	set shouldOpenProject to false
	set shouldCreateThread to false
	set shouldLaunch to true
	set sendMode to "command-enter"
	set startupDelaySeconds to 1.5
	set settleDelaySeconds to 0.35
	set postSendDelaySeconds to 1.0
	set messageTexts to {}
	set i to 1

	repeat while i ≤ (count of argv)
		set arg to item i of argv

		if arg is "--help" then
			return usageText()
		else if arg is "--project" then
			set i to i + 1
			set projectPath to requiredValue(argv, i, "--project")
			set shouldOpenProject to true
		else if arg is "--new-thread" then
			set shouldCreateThread to true
		else if arg is "--message" then
			set i to i + 1
			set end of messageTexts to requiredValue(argv, i, "--message")
		else if arg is "--message-file" then
			set i to i + 1
			set end of messageTexts to readUtf8File(requiredValue(argv, i, "--message-file"))
		else if arg is "--send-mode" then
			set i to i + 1
			set sendMode to normalizeSendMode(requiredValue(argv, i, "--send-mode"))
		else if arg is "--startup-delay" then
			set i to i + 1
			set startupDelaySeconds to (requiredValue(argv, i, "--startup-delay") as real)
		else if arg is "--settle-delay" then
			set i to i + 1
			set settleDelaySeconds to (requiredValue(argv, i, "--settle-delay") as real)
		else if arg is "--post-send-delay" then
			set i to i + 1
			set postSendDelaySeconds to (requiredValue(argv, i, "--post-send-delay") as real)
		else if arg is "--no-launch" then
			set shouldLaunch to false
		else
			error "Unknown argument: " & arg number 64
		end if

		set i to i + 1
	end repeat

	if shouldLaunch then launchCodex(appName, startupDelaySeconds)
	activateCodex(appName)

	if shouldOpenProject then openProjectRoot(appName, projectPath, settleDelaySeconds)

	if shouldCreateThread or ((count of messageTexts) > 0) then
		createThread(appName, settleDelaySeconds)
	end if

	repeat with messageText in messageTexts
		sendPrompt(appName, contents of messageText, sendMode, settleDelaySeconds, postSendDelaySeconds)
	end repeat

	return "ok"
end run

on usageText()
	return "Usage: osascript tools/scripts/codex-app-driver.applescript [options]

Options:
  --project <path>           Open a project root in Codex.app.
  --new-thread               Create a new thread before sending messages.
  --message <text>           Paste and send one message.
  --message-file <path>      Read one message from a UTF-8 text file.
  --send-mode <mode>         One of: command-enter, command-shift-enter, enter-key, paste-only.
  --startup-delay <seconds>  Delay after launching Codex.app. Default: 1.5
  --settle-delay <seconds>   Delay between UI actions. Default: 0.35
  --post-send-delay <secs>   Delay after each send. Default: 1.0
  --no-launch                Assume Codex.app is already running.
  --help                     Print this help.

Examples:
  osascript tools/scripts/codex-app-driver.applescript --project /Users/sigkitten/dev/codex-test --new-thread --message hello
  osascript tools/scripts/codex-app-driver.applescript --project /Users/sigkitten/dev/codex-test --message-file /tmp/prompt.txt --send-mode command-shift-enter"
end usageText

on requiredValue(argv, indexValue, flagName)
	if indexValue > (count of argv) then error "Missing value for " & flagName number 64
	return item indexValue of argv
end requiredValue

on normalizeSendMode(rawMode)
	if rawMode is "command-enter" then return "command_enter"
	if rawMode is "command-shift-enter" then return "command_shift_enter"
	if rawMode is "enter-key" then return "enter_key"
	if rawMode is "paste-only" then return "paste_only"
	error "Unsupported send mode: " & rawMode number 64
end normalizeSendMode

on launchCodex(appName, startupDelaySeconds)
	tell application appName to activate
	delay startupDelaySeconds
	waitForProcess(appName, 10)
end launchCodex

on activateCodex(appName)
	tell application appName to activate
	delay 0.2
end activateCodex

on waitForProcess(appName, timeoutSeconds)
	tell application "System Events"
		repeat with _i from 1 to (timeoutSeconds * 10)
			if exists process appName then return true
			delay 0.1
		end repeat
	end tell
	error "Timed out waiting for process " & appName number 70
end waitForProcess

on waitForWindow(processName, windowName, timeoutSeconds)
	tell application "System Events"
		tell process processName
			repeat with _i from 1 to (timeoutSeconds * 10)
				if exists window windowName then return true
				delay 0.1
			end repeat
		end tell
	end tell
	error "Timed out waiting for window " & windowName number 70
end waitForWindow

on waitForWindowToClose(processName, windowName, timeoutSeconds)
	tell application "System Events"
		tell process processName
			repeat with _i from 1 to (timeoutSeconds * 10)
				if not (exists window windowName) then return true
				delay 0.1
			end repeat
		end tell
	end tell
	error "Timed out waiting for window to close: " & windowName number 70
end waitForWindowToClose

on clickMenuItem(processName, menuBarItemName, menuItemName)
	tell application "System Events"
		tell process processName
			click menu item menuItemName of menu menuBarItemName of menu bar item menuBarItemName of menu bar 1
		end tell
	end tell
end clickMenuItem

on openProjectRoot(appName, projectPath, settleDelaySeconds)
	set projectPath to normalizeProjectPath(projectPath)
	activateCodex(appName)
	clickMenuItem(appName, "File", "Open Folder…")
	waitForWindow(appName, "Select Project Root", 10)

	tell application "System Events"
		tell process appName
			tell window "Select Project Root"
				keystroke "G" using {command down, shift down}
			end tell
			delay settleDelaySeconds
			keystroke projectPath
			key code 36
			delay settleDelaySeconds
			click button "Open" of window "Select Project Root"
		end tell
	end tell

	waitForWindowToClose(appName, "Select Project Root", 10)
	delay settleDelaySeconds
end openProjectRoot

on createThread(appName, settleDelaySeconds)
	activateCodex(appName)
	clickMenuItem(appName, "File", "New Thread")
	delay settleDelaySeconds
end createThread

on sendPrompt(appName, promptText, sendMode, settleDelaySeconds, postSendDelaySeconds)
	set previousClipboard to the clipboard
	try
		activateCodex(appName)
		set the clipboard to promptText
			tell application "System Events"
				tell process appName
					keystroke "v" using command down
					delay settleDelaySeconds
					my pressSendShortcut(sendMode)
				end tell
			end tell
		delay postSendDelaySeconds
	on error errMsg number errNum
		set the clipboard to previousClipboard
		error errMsg number errNum
	end try
	set the clipboard to previousClipboard
end sendPrompt

on pressSendShortcut(sendMode)
	if sendMode is equal to "paste_only" then
		return
	end if
	if sendMode is equal to "enter_key" then
		tell application "System Events" to key code 36
		return
	end if
	if sendMode is equal to "command_enter" then
		tell application "System Events" to key code 36 using {command down}
		return
	end if
	if sendMode is equal to "command_shift_enter" then
		tell application "System Events" to key code 36 using {command down, shift down}
		return
	end if
	error "Unsupported send mode: " & sendMode number 64
end pressSendShortcut

on normalizeProjectPath(projectPath)
	set normalizedPath to do shell script "python3 -c 'import os,sys; print(os.path.abspath(os.path.expanduser(sys.argv[1])))' " & quoted form of projectPath
	return normalizedPath
end normalizeProjectPath

on readUtf8File(filePath)
	set normalizedPath to normalizeProjectPath(filePath)
	return do shell script "cat " & quoted form of normalizedPath
end readUtf8File
