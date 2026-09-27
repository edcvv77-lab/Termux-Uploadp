extends Control

@onready var terminal_panel: Control = %TerminalPanel
@onready var browser_panel: Control = %BrowserPanel
@onready var files_panel: Control = %FilesPanel
@onready var phone_panel: Control = %PhonePanel
@onready var terminal_output: RichTextLabel = %TerminalOutput
@onready var command_input: LineEdit = %CommandInput
@onready var url_input: LineEdit = %UrlInput
@onready var status_label: Label = %StatusLabel
@onready var phone_info: Label = %PhoneInfo

var player_ref: Node = null

func _ready() -> void:
    visible = false
    %CloseButton.pressed.connect(close_desktop)
    %TerminalButton.pressed.connect(func(): _show_panel(terminal_panel))
    %BrowserButton.pressed.connect(func(): _show_panel(browser_panel))
    %FilesButton.pressed.connect(func(): _show_panel(files_panel))
    %PhoneButton.pressed.connect(func(): _show_panel(phone_panel))
    %RunButton.pressed.connect(_run_command)
    %PermissionButton.pressed.connect(_request_termux_permission)
    command_input.text_submitted.connect(func(_text): _run_command())
    %OpenUrlButton.pressed.connect(_open_url)
    %PickFolderButton.pressed.connect(_pick_folder)
    AndroidBridge.termux_result.connect(_on_termux_result)
    AndroidBridge.directory_picked.connect(_on_directory_picked)
    AndroidBridge.bridge_error.connect(_on_bridge_error)

func open_desktop(player: Node) -> void:
    player_ref = player
    visible = true
    if player_ref != null and player_ref.has_method("set_gameplay_controls"):
        player_ref.set_gameplay_controls(false)
    status_label.text = "ANDROID BRIDGE" if AndroidBridge.has_native_bridge() else "PREVIEW"
    phone_info.text = AndroidBridge.device_summary()
    %TermuxState.text = "Termux detected" if AndroidBridge.is_termux_available() else "Termux unavailable / permission not configured"
    command_input.grab_focus()

func close_desktop() -> void:
    visible = false
    if player_ref != null and player_ref.has_method("set_gameplay_controls"):
        player_ref.set_gameplay_controls(true)
    player_ref = null

func _show_panel(panel: Control) -> void:
    for p in [terminal_panel, browser_panel, files_panel, phone_panel]:
        p.visible = p == panel

func _run_command() -> void:
    var command := command_input.text.strip_edges()
    if command.is_empty():
        return

    terminal_output.append_text("\n[color=#7ee787]$ %s[/color]\n" % command)

    match command:
        "clear":
            terminal_output.clear()
        "help":
            terminal_output.append_text("Local: help, clear, status\nAll other commands are sent exactly as typed to your Termux environment when Android bridge permission is enabled.\n")
        "status":
            terminal_output.append_text(AndroidBridge.device_summary() + "\n")
        _:
            var result := AndroidBridge.run_termux_command(command)
            terminal_output.append_text(str(result.message) + (" #%s\n" % result.execution_id if result.has("execution_id") else "\n"))

    command_input.clear()

func _request_termux_permission() -> void:
    if AndroidBridge.request_termux_permission():
        %TermuxState.text = "Permission requested. Also set allow-external-apps=true in ~/.termux/termux.properties."
    else:
        %TermuxState.text = "Android bridge not available."

func _open_url() -> void:
    var result := AndroidBridge.open_url(url_input.text)
    %BrowserMessage.text = str(result.message)

func _pick_folder() -> void:
    var result := AndroidBridge.pick_directory()
    %FilesMessage.text = str(result.message)

func _on_termux_result(execution_id: int, stdout: String, stderr: String, exit_code: int) -> void:
    terminal_output.append_text("\n[color=#8bd5ca]#%d exit=%d[/color]\n" % [execution_id, exit_code])
    if not stdout.is_empty():
        terminal_output.append_text(stdout + ("\n" if not stdout.ends_with("\n") else ""))
    if not stderr.is_empty():
        terminal_output.append_text("[color=#f38ba8]" + stderr + "[/color]\n")

func _on_directory_picked(uri: String) -> void:
    %FilesMessage.text = "Folder permission saved:\n" + uri

func _on_bridge_error(message: String) -> void:
    terminal_output.append_text("\n[color=#f38ba8]Bridge: %s[/color]\n" % message)
