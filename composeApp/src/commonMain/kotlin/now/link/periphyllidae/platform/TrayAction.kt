package now.link.periphyllidae.platform

/**
 * Actions that can be triggered from the system tray
 */
sealed class TrayAction {
    object Play : TrayAction()
    object Pause : TrayAction()
    object Next : TrayAction()
    object Previous : TrayAction()
    object ShowWindow : TrayAction()
    object Exit : TrayAction()
}