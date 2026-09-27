extends Control

var _left := false
var _right := false
var _forward := false
var _back := false

@onready var player := get_tree().get_first_node_in_group("player")

func _ready() -> void:
    %LeftButton.button_down.connect(func(): _left = true; _sync_move())
    %LeftButton.button_up.connect(func(): _left = false; _sync_move())
    %RightButton.button_down.connect(func(): _right = true; _sync_move())
    %RightButton.button_up.connect(func(): _right = false; _sync_move())
    %ForwardButton.button_down.connect(func(): _forward = true; _sync_move())
    %ForwardButton.button_up.connect(func(): _forward = false; _sync_move())
    %BackButton.button_down.connect(func(): _back = true; _sync_move())
    %BackButton.button_up.connect(func(): _back = false; _sync_move())
    %InteractButton.pressed.connect(func():
        if player != null and player.has_method("interact_now"):
            player.interact_now()
    )

func _sync_move() -> void:
    if player == null:
        return
    var horizontal := (1.0 if _right else 0.0) - (1.0 if _left else 0.0)
    var vertical := (1.0 if _forward else 0.0) - (1.0 if _back else 0.0)
    player.set_mobile_move(Vector2(horizontal, vertical))
