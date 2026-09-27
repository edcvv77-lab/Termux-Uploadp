extends CharacterBody3D

@export var walk_speed := 4.2
@export var mouse_sensitivity := 0.0022
@export var touch_sensitivity := 0.0032
@export var gravity := 18.0

@onready var camera: Camera3D = $Camera3D
@onready var ray: RayCast3D = $Camera3D/InteractRay

var controls_enabled := true
var mobile_move := Vector2.ZERO

func _ready() -> void:
    Input.mouse_mode = Input.MOUSE_MODE_CAPTURED

func _unhandled_input(event: InputEvent) -> void:
    if not controls_enabled:
        return

    if event is InputEventMouseMotion and Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
        _apply_look(event.relative, mouse_sensitivity)

    if event is InputEventScreenDrag:
        _apply_look(event.relative, touch_sensitivity)

    if event is InputEventKey and event.pressed and not event.echo:
        if event.keycode == KEY_E:
            _try_interact()
        elif event.keycode == KEY_ESCAPE:
            Input.mouse_mode = Input.MOUSE_MODE_VISIBLE

func _apply_look(relative: Vector2, sensitivity: float) -> void:
    rotate_y(-relative.x * sensitivity)
    camera.rotation.x = clamp(
        camera.rotation.x - relative.y * sensitivity,
        deg_to_rad(-85.0),
        deg_to_rad(85.0)
    )

func _physics_process(delta: float) -> void:
    if not controls_enabled:
        velocity = Vector3.ZERO
        return

    var keyboard_move := Vector2.ZERO
    if Input.is_key_pressed(KEY_A):
        keyboard_move.x -= 1.0
    if Input.is_key_pressed(KEY_D):
        keyboard_move.x += 1.0
    if Input.is_key_pressed(KEY_W):
        keyboard_move.y += 1.0
    if Input.is_key_pressed(KEY_S):
        keyboard_move.y -= 1.0

    var input_vec := keyboard_move if keyboard_move.length() > 0.0 else mobile_move
    input_vec = input_vec.normalized()

    var basis := global_transform.basis
    var direction := basis.x * input_vec.x + -basis.z * input_vec.y
    direction.y = 0.0
    direction = direction.normalized()

    velocity.x = direction.x * walk_speed
    velocity.z = direction.z * walk_speed

    if not is_on_floor():
        velocity.y -= gravity * delta
    else:
        velocity.y = 0.0

    move_and_slide()

func _try_interact() -> void:
    if not ray.is_colliding():
        return
    var target := ray.get_collider()
    if target != null and target.has_method("interact"):
        target.interact(self)

func interact_now() -> void:
    if controls_enabled:
        _try_interact()

func set_mobile_move(value: Vector2) -> void:
    mobile_move = value.limit_length(1.0)

func set_gameplay_controls(enabled: bool) -> void:
    controls_enabled = enabled
    mobile_move = Vector2.ZERO
    Input.mouse_mode = Input.MOUSE_MODE_CAPTURED if enabled else Input.MOUSE_MODE_VISIBLE
