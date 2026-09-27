extends CharacterBody3D

@export var walk_speed := 4.2
@export var mouse_sensitivity := 0.0022
@export var gravity := 18.0

@onready var camera: Camera3D = $Camera3D
@onready var ray: RayCast3D = $Camera3D/InteractRay

var controls_enabled := true

func _ready() -> void:
    Input.mouse_mode = Input.MOUSE_MODE_CAPTURED

func _unhandled_input(event: InputEvent) -> void:
    if event is InputEventMouseMotion and controls_enabled and Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
        rotate_y(-event.relative.x * mouse_sensitivity)
        camera.rotation.x = clamp(
            camera.rotation.x - event.relative.y * mouse_sensitivity,
            deg_to_rad(-85.0),
            deg_to_rad(85.0)
        )

    if event is InputEventKey and event.pressed and not event.echo:
        if event.keycode == KEY_E and controls_enabled:
            _try_interact()
        elif event.keycode == KEY_ESCAPE and controls_enabled:
            Input.mouse_mode = Input.MOUSE_MODE_VISIBLE

func _physics_process(delta: float) -> void:
    if not controls_enabled:
        velocity = Vector3.ZERO
        return

    var input_vec := Vector2.ZERO
    if Input.is_key_pressed(KEY_A):
        input_vec.x -= 1.0
    if Input.is_key_pressed(KEY_D):
        input_vec.x += 1.0
    if Input.is_key_pressed(KEY_W):
        input_vec.y += 1.0
    if Input.is_key_pressed(KEY_S):
        input_vec.y -= 1.0
    input_vec = input_vec.normalized()

    var basis := global_transform.basis
    var direction := (basis.x * input_vec.x + -basis.z * input_vec.y)
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

func set_gameplay_controls(enabled: bool) -> void:
    controls_enabled = enabled
    Input.mouse_mode = Input.MOUSE_MODE_CAPTURED if enabled else Input.MOUSE_MODE_VISIBLE
