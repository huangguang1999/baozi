# iOS TestFlight Checklist

1. Confirm `apps/ios/project.yml` bundle ID/version/build settings are correct.
2. Build/archive in Xcode from `apps/ios/Litter.xcodeproj`.
3. Update `docs/releases/testflight-whats-new.md` with changelog bullets for this build.
4. Upload via `./apps/ios/scripts/testflight-upload.sh` (script auto-applies What to Test notes, assigns internal and external beta groups, submits Beta App Review by default, and auto-bumps to the next patch version if the committed repo version has already shipped live).
5. Validate processing in App Store Connect.
6. Confirm the build is attached to both internal and external TestFlight groups.
7. Confirm Beta App Review submission/approval state for the external build, then verify release notes and tester instructions.
8. If the workflow advanced to a new beta patch version, confirm `apps/ios/project.yml` and `docs/releases/testflight-whats-new.md` were updated for the next cycle.
9. Smoke test install + login/session/message flow.
