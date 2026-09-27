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
    command_input.text_submitted.connect(func(_text): _run_command())
    %OpenUrlButton.pressed.connect(_open_url)
    %PickFolderButton.pressed.connect(_pick_folder)

func open_desktop(player: Node) -> void:
    player_ref = player
    visible = true
    if player_ref != null and player_ref.has_method("set_gameplay_controls"):
        player_ref.set_gameplay_controls(false)
    status_label.text = "REAL bridge" if AndroidBridge.has_native_bridge() else "MOCK bridge"
    phone_info.text = AndroidBridge.device_summary()
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
            terminal_output.append_text("help, clear, status, pwd, ls\nOther commands are forwarded to the Android/Termux bridge when installed.\n")
        "status":
            terminal_output.append_text(AndroidBridge.device_summary() + "\n")
        "pwd":
            terminal_output.append_text("/data/data/com.termux/files/home  [preview]\n")
        "ls":
            terminal_output.append_text("Downloads  Documents  Projects  [preview]\n")
        _:
            var result := AndroidBridge.run_termux_command(command)
            terminal_output.append_text(str(result.message) + "\n")

    command_input.clear()

func _open_url() -> void:
    var result := AndroidBridge.open_url(url_input.text)
    %BrowserMessage.text = str(result.message)

func _pick_folder() -> void:
    var result := AndroidBridge.pick_directory()
    %FilesMessage.text = str(result.message)
