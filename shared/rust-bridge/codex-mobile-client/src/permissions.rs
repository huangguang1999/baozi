use crate::types::{AppAskForApproval, AppSandboxPolicy};

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum AppThreadPermissionPreset {
    Unknown,
    Supervised,
    FullAccess,
    Custom,
}

#[uniffi::export]
pub fn thread_permission_preset(
    approval_policy: Option<AppAskForApproval>,
    sandbox_policy: Option<AppSandboxPolicy>,
) -> AppThreadPermissionPreset {
    match (approval_policy, sandbox_policy) {
        (Some(AppAskForApproval::OnRequest), Some(AppSandboxPolicy::WorkspaceWrite { .. })) => {
            AppThreadPermissionPreset::Supervised
        }
        (Some(AppAskForApproval::Never), Some(AppSandboxPolicy::DangerFullAccess)) => {
            AppThreadPermissionPreset::FullAccess
        }
        (Some(_), _) | (_, Some(_)) => AppThreadPermissionPreset::Custom,
        (None, None) => AppThreadPermissionPreset::Unknown,
    }
}

#[uniffi::export]
pub fn thread_permissions_are_authoritative(
    approval_policy: Option<AppAskForApproval>,
    sandbox_policy: Option<AppSandboxPolicy>,
) -> bool {
    approval_policy.is_some() || sandbox_policy.is_some()
}

#[cfg(test)]
mod tests {
    use super::{
        AppThreadPermissionPreset, thread_permission_preset, thread_permissions_are_authoritative,
    };
    use crate::types::{AppAskForApproval, AppReadOnlyAccess, AppSandboxPolicy};

    #[test]
    fn permission_preset_classifies_known_pairs() {
        assert_eq!(
            thread_permission_preset(
                Some(AppAskForApproval::OnRequest),
                Some(AppSandboxPolicy::WorkspaceWrite {
                    writable_roots: Vec::new(),
                    read_only_access: AppReadOnlyAccess::FullAccess,
                    network_access: false,
                    exclude_tmpdir_env_var: false,
                    exclude_slash_tmp: false,
                }),
            ),
            AppThreadPermissionPreset::Supervised
        );
        assert_eq!(
            thread_permission_preset(
                Some(AppAskForApproval::Never),
                Some(AppSandboxPolicy::DangerFullAccess),
            ),
            AppThreadPermissionPreset::FullAccess
        );
    }

    #[test]
    fn permission_preset_marks_partial_or_missing_permissions() {
        assert_eq!(
            thread_permission_preset(Some(AppAskForApproval::OnFailure), None),
            AppThreadPermissionPreset::Custom
        );
        assert_eq!(
            thread_permission_preset(None, None),
            AppThreadPermissionPreset::Unknown
        );
        assert!(thread_permissions_are_authoritative(
            Some(AppAskForApproval::OnFailure),
            None,
        ));
        assert!(!thread_permissions_are_authoritative(None, None));
    }
}
