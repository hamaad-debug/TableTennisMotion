extends Node3D
## Lightweight 3D vertical slice for the TV side of Rally.
## Receives the existing Android phone controller's UDP H,power,aim,side,top packets.

const TABLE_TOP := 0.76
const TABLE_WIDTH := 1.525
const TABLE_LENGTH := 2.74
const BALL_RADIUS := 0.020
const GRAVITY := 9.81
const STADIUMS := ["TOKYO SKYDOME", "PARIS GRAND ARENA", "RIO SAMBA HALL", "LONDON CHAMPIONS COURT"]
const MATCHUPS := [["INDIA", "JAPAN"], ["BRAZIL", "FRANCE"], ["CHINA", "USA"], ["SOUTH KOREA", "GERMANY"], ["SWEDEN", "AUSTRALIA"], ["EGYPT", "SPAIN"], ["CANADA", "ITALY"], ["MEXICO", "SINGAPORE"]]

var ball: MeshInstance3D
var ball_shadow: MeshInstance3D
var ball_mesh: SphereMesh
var near_player: Node3D
var far_player: Node3D
var near_racket: Node3D
var far_racket: Node3D
var camera: Camera3D
var score_label: Label
var status_label: Label
var help_label: Label
var stadium_label: Label
var arena_lights: Array[OmniLight3D] = []
var stadium_banners: Array[MeshInstance3D] = []
var table_surface_mesh: MeshInstance3D
var trail_mesh: ImmediateMesh
var trail_instance: MeshInstance3D
var trail_points: Array[Vector3] = []

var receiver := PacketPeerUDP.new()
var beacon := PacketPeerUDP.new()
var network_ready := false
var network_timer := 0.0
var phone_last_seen := 0.0

var ball_position := Vector3.ZERO
var ball_velocity := Vector3.ZERO
var angular_spin := Vector3.ZERO
var last_hitter := "near"
var bounces_on_receiver := 0
var rally_active := false
var near_score := 0
var far_score := 0
var near_x := 0.0
var far_x := 0.0
var aim_keyboard := 0.0
var side_spin_keyboard := 0.0
var top_spin_keyboard := 0.0
var near_swing := 0.0
var far_swing := 0.0
var ai_think := 0.0
var point_pause := 0.0
var serve_timer := 0.0
var server_side := "near"
var game_over := false
var two_player := true
var stadium_index := 0
var matchup_index := 0
var banner := "PHONE = PLAYER 1   ·   TV REMOTE = PLAYER 2"

func _ready() -> void:
	_build_world()
	_build_players()
	_build_ball()
	_build_ui()
	_start_network()
	_reset_ball("near")

func _process(delta: float) -> void:
	var dt := minf(delta, 0.033)
	_process_network()
	_update_players(dt)
	_update_point_pause(dt)
	_update_ball(dt)
	_update_animation(dt)
	_update_scoreboard()
	_update_trail()

func _build_world() -> void:
	var world := WorldEnvironment.new()
	var environment := Environment.new()
	environment.background_mode = Environment.BG_SKY
	var sky := Sky.new()
	var sky_material := ProceduralSkyMaterial.new()
	sky_material.sky_top_color = Color("061326")
	sky_material.sky_horizon_color = Color("183a51")
	sky_material.ground_bottom_color = Color("071421")
	sky_material.ground_horizon_color = Color("243a4c")
	sky.sky_material = sky_material
	environment.sky = sky
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.ambient_light_color = Color("9cb9d0")
	environment.ambient_light_energy = 0.48
	environment.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	world.environment = environment
	add_child(world)

	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-48, -24, 0)
	sun.light_color = Color("b6dcf3")
	sun.light_energy = 0.68
	sun.shadow_enabled = true
	add_child(sun)
	for side in [-1.0, 1.0]:
		var lamp := OmniLight3D.new()
		lamp.position = Vector3(side * 4.4, 5.7, -0.4)
		lamp.light_color = Color("6bd9f2") if side < 0 else Color("ffd38a")
		lamp.light_energy = 2.1
		lamp.omni_range = 11.0
		lamp.shadow_enabled = false
		add_child(lamp)
		arena_lights.append(lamp)

	var floor_material := _mat(Color("071b2b"), 0.86)
	_add_box("Stadium floor", Vector3(0, -0.17, 0), Vector3(22, 0.25, 22), floor_material)
	var court_material := _mat(Color("0a3047"), 0.78)
	_add_box("Court floor", Vector3(0, -0.035, 0), Vector3(8.6, 0.035, 7.6), court_material)
	_add_stadium_seating()
	_add_table()
	_add_stadium_banners()
	_add_score_arch()
	_add_camera()
	_apply_stadium()

func _add_table() -> void:
	var blue := _mat(Color("0872a0"), 0.32)
	var trim := _mat(Color("102739"), 0.56)
	var white := _mat(Color("e9f7fb"), 0.46)
	var metal := _mat(Color("50677a"), 0.35, 0.7)
	_add_box("Table apron", Vector3(0, TABLE_TOP - 0.045, 0), Vector3(TABLE_WIDTH + 0.07, 0.10, TABLE_LENGTH + 0.07), trim)
	table_surface_mesh = _add_box("Blue playing surface", Vector3(0, TABLE_TOP + 0.015, 0), Vector3(TABLE_WIDTH, 0.03, TABLE_LENGTH), blue)
	_add_box("Left sideline", Vector3(-TABLE_WIDTH / 2 + 0.009, TABLE_TOP + 0.034, 0), Vector3(0.018, 0.006, TABLE_LENGTH), white)
	_add_box("Right sideline", Vector3(TABLE_WIDTH / 2 - 0.009, TABLE_TOP + 0.034, 0), Vector3(0.018, 0.006, TABLE_LENGTH), white)
	_add_box("Endline near", Vector3(0, TABLE_TOP + 0.034, TABLE_LENGTH / 2 - 0.009), Vector3(TABLE_WIDTH, 0.006, 0.018), white)
	_add_box("Endline far", Vector3(0, TABLE_TOP + 0.034, -TABLE_LENGTH / 2 + 0.009), Vector3(TABLE_WIDTH, 0.006, 0.018), white)
	_add_box("Center line", Vector3(0, TABLE_TOP + 0.035, 0), Vector3(0.012, 0.006, TABLE_LENGTH), _mat(Color("c6e7ef"), 0.46))
	for x in [-0.57, 0.57]:
		for z in [-1.0, 1.0]:
			_add_box("Steel table leg", Vector3(x, 0.35, z), Vector3(0.045, 0.73, 0.045), metal)
	_add_box("Net", Vector3(0, TABLE_TOP + 0.105, 0), Vector3(TABLE_WIDTH + 0.08, 0.18, 0.018), _mat(Color("d5e4ea"), 0.88))
	_add_box("Net tape", Vector3(0, TABLE_TOP + 0.198, 0), Vector3(TABLE_WIDTH + 0.10, 0.018, 0.028), white)
	for x in [-TABLE_WIDTH / 2 - 0.035, TABLE_WIDTH / 2 + 0.035]:
		_add_box("Net post", Vector3(x, TABLE_TOP + 0.10, 0), Vector3(0.025, 0.23, 0.04), metal)
	var post_material := _mat(Color("183248"), 0.5)
	for z in [-1.05, 1.05]:
		_add_box("Table support", Vector3(0, 0.29, z), Vector3(1.24, 0.07, 0.05), post_material)

func _add_stadium_banners() -> void:
	for side in [-1.0, 1.0]:
		var banner_mesh := _add_box("Country tour banner", Vector3(side * 4.75, 2.65, 0), Vector3(0.12, 0.48, 6.4), _mat(Color("2c8fc0"), 0.66))
		stadium_banners.append(banner_mesh)

func _apply_stadium() -> void:
	var light_sets := [
		[Color("61d9fa"), Color("ffce82")],
		[Color("ec78d8"), Color("78bbff")],
		[Color("ffe08a"), Color("ff8757")],
		[Color("73e0aa"), Color("75adff")]
	]
	var table_colors := [Color("0872a0"), Color("17477f"), Color("11826f"), Color("144c66")]
	var banner_colors := [Color("2879a6"), Color("824b9f"), Color("aa6239"), Color("317a66")]
	for i in range(arena_lights.size()):
		arena_lights[i].light_color = light_sets[stadium_index][i]
	if table_surface_mesh:
		table_surface_mesh.material_override = _mat(table_colors[stadium_index], 0.32)
	for banner_mesh in stadium_banners:
		banner_mesh.material_override = _mat(banner_colors[stadium_index], 0.66)
	if stadium_label:
		stadium_label.text = STADIUMS[stadium_index]

func _add_stadium_seating() -> void:
	var seat_palette := [Color("263b55"), Color("304968"), Color("17334b"), Color("39536a"), Color("213f5b")]
	var rng := RandomNumberGenerator.new()
	rng.seed = 1937
	var crowd_positions: Array[Vector3] = []
	var crowd_colors: Array[Color] = []
	for row in range(7):
		var z := -4.2 - float(row) * 0.62
		var height := 1.3 + float(row) * 0.17
		_add_box("Far grandstand row %d" % row, Vector3(0, height - 0.18, z), Vector3(13.4, 0.28, 0.42), _mat(Color("101f31"), 0.92))
		for seat in range(52):
			var x := -6.35 + float(seat) * 0.25
			var color: Color = seat_palette[rng.randi_range(0, seat_palette.size() - 1)]
			crowd_positions.append(Vector3(x, height + rng.randf_range(-0.10, 0.10), z + rng.randf_range(-0.08, 0.08)))
			crowd_colors.append(color)
	for xside in [-1.0, 1.0]:
		_add_box("Side grandstand", Vector3(xside * 5.4, 1.45, 0), Vector3(0.55, 2.9, 7.2), _mat(Color("102237"), 0.92))
		for row in range(5):
			for seat in range(18):
				crowd_positions.append(Vector3(xside * (5.0 + float(row) * 0.19), 0.65 + float(row) * 0.34, -3.1 + float(seat) * 0.36))
				crowd_colors.append(seat_palette[(seat + row) % seat_palette.size()])
	var crowd_mesh := MultiMesh.new()
	crowd_mesh.transform_format = MultiMesh.TRANSFORM_3D
	crowd_mesh.use_colors = true
	var crowd_sphere := SphereMesh.new()
	crowd_sphere.radius = 0.065
	crowd_sphere.height = 0.15
	crowd_sphere.radial_segments = 6
	crowd_sphere.rings = 4
	crowd_mesh.mesh = crowd_sphere
	crowd_mesh.instance_count = crowd_positions.size()
	var audience := MultiMeshInstance3D.new()
	audience.name = "Instanced audience"
	audience.multimesh = crowd_mesh
	var audience_material := _mat(Color.WHITE, 0.96)
	audience_material.vertex_color_use_as_albedo = true
	audience.material_override = audience_material
	add_child(audience)
	for i in range(crowd_positions.size()):
		crowd_mesh.set_instance_transform(i, Transform3D(Basis.IDENTITY, crowd_positions[i]))
		crowd_mesh.set_instance_color(i, crowd_colors[i])

func _add_score_arch() -> void:
	var support := _mat(Color("091522"), 0.62)
	_add_box("Scoreboard frame", Vector3(0, 5.1, -5.65), Vector3(6.8, 2.25, 0.32), support)
	_add_box("Scoreboard screen", Vector3(0, 5.1, -5.46), Vector3(6.35, 1.82, 0.06), _mat(Color("06101e"), 0.28))
	for x in [-3.25, 3.25]:
		_add_box("Stadium truss", Vector3(x, 5.05, -5.6), Vector3(0.12, 2.3, 0.42), _mat(Color("34495b"), 0.42, 0.55))

func _add_camera() -> void:
	camera = Camera3D.new()
	camera.position = Vector3(0, 3.55, 7.25)
	camera.fov = 54.0
	camera.current = true
	add_child(camera)
	camera.look_at(Vector3(0, 0.76, -0.05), Vector3.UP)

func _build_players() -> void:
	near_player = _make_player("PLAYER 1", Color("21c8d9"), 1.98, 1.0)
	far_player = _make_player("PLAYER 2", Color("f25467"), -1.98, -1.0)
	near_racket = near_player.get_node("RacketPivot")
	far_racket = far_player.get_node("RacketPivot")
	add_child(near_player)
	add_child(far_player)

func _make_player(player_name: String, shirt: Color, z: float, facing: float) -> Node3D:
	var root := Node3D.new()
	root.name = player_name
	root.position = Vector3(0, 0, z)
	var body := _capsule(player_name + " shirt", 0.34, 0.80, _mat(shirt, 0.68))
	body.position = Vector3(0, 1.05, 0)
	root.add_child(body)
	var shorts := _capsule(player_name + " shorts", 0.31, 0.40, _mat(Color("14283a"), 0.86))
	shorts.position = Vector3(0, 0.48, 0)
	root.add_child(shorts)
	var head := _sphere(player_name + " head", Vector3(0.22, 0.25, 0.22), _mat(Color("d9a17c"), 0.75))
	head.position = Vector3(0, 1.72, 0)
	root.add_child(head)
	var hair := _sphere(player_name + " hair", Vector3(0.225, 0.10, 0.225), _mat(Color("202735"), 0.88))
	hair.position = Vector3(0, 1.84, 0)
	root.add_child(hair)
	for side in [-1.0, 1.0]:
		var leg := _capsule("Leg", 0.105, 0.69, _mat(Color("182433"), 0.91))
		leg.position = Vector3(side * 0.17, 0.10, 0)
		root.add_child(leg)
		var shoe := _box_mesh("Shoe", Vector3(0.18, 0.09, 0.31), _mat(Color("e8eef1"), 0.8))
		shoe.position = Vector3(side * 0.17, -0.27, facing * 0.10)
		root.add_child(shoe)
		var arm := _capsule("Arm", 0.075, 0.63, _mat(Color("d9a17c"), 0.75))
		arm.position = Vector3(side * 0.39, 1.08, 0)
		arm.rotation.z = side * 0.22
		root.add_child(arm)
	var pivot := Node3D.new()
	pivot.name = "RacketPivot"
	pivot.position = Vector3(facing * 0.45, 1.30, facing * -0.02)
	root.add_child(pivot)
	var handle := _cylinder("Racket handle", 0.035, 0.34, _mat(Color("d5b18a"), 0.66))
	handle.position.y = -0.18
	pivot.add_child(handle)
	var paddle := _cylinder("Racket blade", 0.19, 0.045, _mat(Color("e64759") if shirt.r > 0.7 else Color("222d42"), 0.45))
	paddle.position.y = 0.02
	paddle.rotation.x = PI / 2.0
	pivot.add_child(paddle)
	var rim := _torus("Racket edge", 0.19, 0.014, _mat(Color("f1ccd1"), 0.42))
	rim.position.y = 0.02
	rim.rotation.x = PI / 2.0
	pivot.add_child(rim)
	return root

func _build_ball() -> void:
	ball_mesh = SphereMesh.new()
	ball_mesh.radius = BALL_RADIUS
	ball_mesh.height = BALL_RADIUS * 2.0
	ball_mesh.radial_segments = 20
	ball_mesh.rings = 12
	ball = MeshInstance3D.new()
	ball.name = "Celluloid ball"
	ball.mesh = ball_mesh
	ball.material_override = _mat(Color("ffd46a"), 0.36)
	add_child(ball)
	ball_shadow = _sphere("Ball shadow", Vector3(0.10, 0.006, 0.08), _mat(Color(0.0, 0.0, 0.0, 0.36), 1.0))
	add_child(ball_shadow)
	trail_mesh = ImmediateMesh.new()
	trail_instance = MeshInstance3D.new()
	trail_instance.mesh = trail_mesh
	var trail_material := StandardMaterial3D.new()
	trail_material.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	trail_material.vertex_color_use_as_albedo = true
	trail_material.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	trail_instance.material_override = trail_material
	add_child(trail_instance)

func _build_ui() -> void:
	var layer := CanvasLayer.new()
	add_child(layer)
	var root := Control.new()
	root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	layer.add_child(root)
	score_label = _label(root, "SCORE", 28, Vector2(0.5, 0.045), Vector2(0.5, 0.16), Color("f5fbff"), true)
	status_label = _label(root, "READY", 21, Vector2(0.20, 0.77), Vector2(0.80, 0.85), Color("ffe19a"), true)
	stadium_label = _label(root, "", 16, Vector2(0.61, 0.045), Vector2(0.98, 0.10), Color("d6edf8"), true)
	help_label = _label(root, "PHONE: swing / tilt / twist     TV REMOTE: OK hit · LEFT/RIGHT aim · UP stadium · DOWN matchup · MENU AI", 15, Vector2(0.04, 0.92), Vector2(0.96, 0.99), Color("c3d5e1"), false)
	status_label.text = banner

func _label(parent: Control, value: String, size: int, from: Vector2, to: Vector2, tint: Color, bold: bool) -> Label:
	var label := Label.new()
	label.text = value
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	label.add_theme_font_size_override("font_size", size)
	label.add_theme_color_override("font_color", tint)
	label.add_theme_color_override("font_shadow_color", Color(0.0, 0.0, 0.0, 0.9))
	label.add_theme_constant_override("shadow_offset_x", 2)
	label.add_theme_constant_override("shadow_offset_y", 2)
	label.add_theme_font_override("font", ThemeDB.fallback_font)
	label.add_theme_font_size_override("font_size", size)
	label.add_theme_color_override("font_outline_color", Color("0b1522"))
	label.add_theme_constant_override("outline_size", 3)
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	label.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	label.anchor_left = from.x
	label.anchor_top = from.y
	label.anchor_right = to.x
	label.anchor_bottom = to.y
	label.offset_left = 0
	label.offset_top = 0
	label.offset_right = 0
	label.offset_bottom = 0
	parent.add_child(label)
	return label

func _start_network() -> void:
	var err := receiver.bind(48002, "0.0.0.0")
	if err != OK:
		banner = "NETWORK PORT 48002 UNAVAILABLE · KEYBOARD MODE ACTIVE"
		return
	beacon.set_broadcast_enabled(true)
	network_ready = true

func _process_network() -> void:
	if network_ready:
		while receiver.get_available_packet_count() > 0:
			var packet := receiver.get_packet().get_string_from_utf8().strip_edges()
			var parts := packet.split(",")
			if packet == "P":
				phone_last_seen = Time.get_ticks_msec() / 1000.0
			elif parts.size() >= 3 and parts[0] == "H":
				phone_last_seen = Time.get_ticks_msec() / 1000.0
				var power := clampf(parts[1].to_float(), 0.0, 1.0)
				var aim := clampf(parts[2].to_float(), -1.0, 1.0)
				var side := clampf(parts[3].to_float(), -1.0, 1.0) if parts.size() > 3 else 0.0
				var top := clampf(parts[4].to_float(), -1.0, 1.0) if parts.size() > 4 else 0.0
				_try_hit("near", power, aim, side, top)
		network_timer -= get_process_delta_time()
		if network_timer <= 0.0:
			network_timer = 0.8
			_send_discovery_beacon()
	var now := Time.get_ticks_msec() / 1000.0
	if now - phone_last_seen < 2.5:
		banner = "PHONE CONNECTED  ·  %s vs %s" % [MATCHUPS[matchup_index][0], MATCHUPS[matchup_index][1]]
	else:
		banner = "WAITING FOR PHONE  ·  KEYBOARD MODE ACTIVE"

func _send_discovery_beacon() -> void:
	beacon.set_dest_address("255.255.255.255", 48001)
	beacon.put_packet("TTTV".to_utf8_buffer())
	for address in IP.get_local_addresses():
		var octets := address.split(".")
		if octets.size() == 4 and octets[0] != "127" and octets[0].is_valid_int() and octets[0] in ["10", "172", "192"]:
			beacon.set_dest_address("%s.%s.%s.255" % [octets[0], octets[1], octets[2]], 48001)
			beacon.put_packet("TTTV".to_utf8_buffer())

func _update_players(dt: float) -> void:
	var left_key := KEY_A if two_player else KEY_LEFT
	var right_key := KEY_D if two_player else KEY_RIGHT
	if Input.is_key_pressed(left_key): near_x -= 2.7 * dt
	if Input.is_key_pressed(right_key): near_x += 2.7 * dt
	if two_player:
		if Input.is_key_pressed(KEY_J): far_x -= 2.7 * dt
		if Input.is_key_pressed(KEY_L): far_x += 2.7 * dt
	near_x = clampf(near_x, -1.25, 1.25)
	far_x = clampf(far_x, -1.25, 1.25)
	if rally_active:
		if last_hitter == "far": near_x = lerpf(near_x, clampf(ball_position.x, -1.15, 1.15), minf(1.0, dt * 2.5))
		else: far_x = lerpf(far_x, clampf(ball_position.x, -1.15, 1.15), minf(1.0, dt * 2.0))
	near_player.position.x = near_x
	far_player.position.x = far_x
	if Input.is_key_pressed(KEY_Q): side_spin_keyboard = -1.0
	elif Input.is_key_pressed(KEY_E): side_spin_keyboard = 1.0
	else: side_spin_keyboard = move_toward(side_spin_keyboard, 0.0, dt * 2.5)
	if Input.is_key_pressed(KEY_R): top_spin_keyboard = 1.0
	elif Input.is_key_pressed(KEY_F): top_spin_keyboard = -1.0
	else: top_spin_keyboard = move_toward(top_spin_keyboard, 0.0, dt * 2.5)

func _update_ball(dt: float) -> void:
	if rally_active:
		# Magnus lift: sidespin bends the shot, topspin dives it into the table.
		var magnus := angular_spin.cross(ball_velocity) * 0.00055
		ball_velocity += (Vector3.DOWN * GRAVITY + magnus - ball_velocity * 0.012) * dt
		ball_position += ball_velocity * dt
		angular_spin = angular_spin.lerp(Vector3.ZERO, dt * 0.20)
		ball.rotation += angular_spin * dt
		if absf(ball_position.z) < 0.025 and ball_position.y < TABLE_TOP + 0.15 and absf(ball_velocity.z) > 0.2:
			_award_point("far" if last_hitter == "near" else "near", "NET")
			return
		if ball_position.y <= TABLE_TOP + BALL_RADIUS and absf(ball_position.x) <= TABLE_WIDTH / 2 and absf(ball_position.z) <= TABLE_LENGTH / 2 and ball_velocity.y < 0:
			ball_position.y = TABLE_TOP + BALL_RADIUS
			ball_velocity.y = absf(ball_velocity.y) * 0.86
			ball_velocity.x += angular_spin.y * 0.018
			ball_velocity.z += angular_spin.x * 0.014
			angular_spin *= Vector3(0.70, 0.68, 0.78)
			var receiver_side := "far" if last_hitter == "near" else "near"
			if _ball_on_side(receiver_side):
				bounces_on_receiver += 1
				if bounces_on_receiver >= 2:
					_award_point(last_hitter, "SECOND BOUNCE")
					return
		if ball_position.y < 0.03 or absf(ball_position.x) > 2.8 or absf(ball_position.z) > 5.5:
			_award_point("far" if last_hitter == "near" else "near", "OUT")
			return
	if not two_player and last_hitter == "near" and _ball_on_side("far") and bounces_on_receiver > 0:
			ai_think += dt
			if ai_think > 0.18 and ball_position.y < 1.45:
				_try_hit("far", 0.48, signf(-ball_position.x) * 0.60, randf_range(-0.45, 0.45), randf_range(-0.35, 0.35))
	ball.position = ball_position
	ball_shadow.position = Vector3(ball_position.x, TABLE_TOP + 0.006, ball_position.z)

func _ball_on_side(side: String) -> bool:
	return ball_position.z > 0.0 if side == "near" else ball_position.z < 0.0

func _try_hit(side: String, power: float, aim: float, side_spin: float, top_spin: float) -> void:
	if game_over: return
	if rally_active:
		if side == "near" and (last_hitter != "far" or bounces_on_receiver < 1 or ball_position.z < 0.1 or ball_position.z > 2.55): return
		if side == "far" and (last_hitter != "near" or bounces_on_receiver < 1 or ball_position.z > -0.1 or ball_position.z < -2.55): return
		var player_x := near_x if side == "near" else far_x
		if absf(ball_position.x - player_x) > 1.18: return
		if ball_position.y < TABLE_TOP + 0.02 or ball_position.y > 2.3: return
		if not _ball_on_side("far" if side == "near" else "near"): return
		last_hitter = side
		bounces_on_receiver = 0
	else:
		if side != server_side: return
		if side == "near" and ball_position.z < 0.0: return
		if side == "far" and ball_position.z > 0.0: return
		last_hitter = side
		bounces_on_receiver = 0
	var horizontal := clampf(aim + side_spin * 0.12, -1.0, 1.0)
	var direction := -1.0 if side == "near" else 1.0
	ball_position.y = maxf(ball_position.y, TABLE_TOP + 0.22)
	ball_velocity = Vector3(horizontal * (1.1 + power * 1.4), 2.2 + power * 1.6 + top_spin * 0.24, direction * (3.2 + power * 2.0))
	angular_spin = Vector3(top_spin * 23.0, side_spin * 26.0, horizontal * 5.0)
	near_swing = 1.0 if side == "near" else near_swing
	far_swing = 1.0 if side == "far" else far_swing
	rally_active = true
	ai_think = 0.0
	trail_points.clear()

func _award_point(winner: String, reason: String) -> void:
	rally_active = false
	point_pause = 1.5
	if winner == "near": near_score += 1
	else: far_score += 1
	var winner_name: String = MATCHUPS[matchup_index][0] if winner == "near" else MATCHUPS[matchup_index][1]
	banner = "%s POINT  ·  %s" % [winner_name, reason]
	status_label.text = banner
	var match_over: bool = (near_score >= 11 or far_score >= 11) and abs(near_score - far_score) >= 2
	if match_over:
		game_over = true
		point_pause = 0.0
		banner = "%s WINS THE MATCH  ·  PRESS OK TO REMATCH" % winner_name
		status_label.text = banner

func _update_point_pause(dt: float) -> void:
	if game_over: return
	if point_pause > 0.0:
		point_pause = maxf(0.0, point_pause - dt)
		if point_pause == 0.0:
			var total_points := near_score + far_score
			server_side = "near" if int(total_points / 2) % 2 == 0 else "far"
			_reset_ball(server_side)
	if not two_player and not rally_active and server_side == "far":
		serve_timer += dt
		if serve_timer >= 0.9:
			serve_timer = 0.0
			_try_hit("far", 0.46, randf_range(-0.55, 0.55), randf_range(-0.3, 0.3), randf_range(-0.3, 0.3))

func _reset_ball(server: String) -> void:
	server_side = server
	game_over = false
	serve_timer = 0.0
	last_hitter = server
	bounces_on_receiver = 0
	rally_active = false
	ball_position = Vector3(0, TABLE_TOP + 0.56, 1.12 if server == "near" else -1.12)
	ball_velocity = Vector3.ZERO
	angular_spin = Vector3.ZERO
	trail_points.clear()
	near_swing = 0.0
	far_swing = 0.0
	status_label.text = "PHONE: SWING TO SERVE" if server == "near" else "PLAYER 2: PRESS OK TO SERVE"

func _update_animation(dt: float) -> void:
	near_swing = move_toward(near_swing, 0.0, dt * 4.4)
	far_swing = move_toward(far_swing, 0.0, dt * 4.4)
	near_racket.rotation.z = -0.70 + side_spin_keyboard * 0.26 - near_swing * 1.45
	far_racket.rotation.z = 0.70 - far_swing * 1.45
	if ball:
		ball.position = ball_position
		ball_shadow.position = Vector3(ball_position.x, TABLE_TOP + 0.006, ball_position.z)

func _update_scoreboard() -> void:
	if score_label:
		score_label.text = "%s     %d  :  %d     %s" % [MATCHUPS[matchup_index][0], near_score, far_score, MATCHUPS[matchup_index][1]]
	if stadium_label:
		stadium_label.text = STADIUMS[stadium_index]
	if status_label and rally_active:
		status_label.text = banner
	if status_label and not rally_active and banner.begins_with("WAITING"):
		status_label.text = banner
	if help_label:
		help_label.text = "PHONE: tilt=aim · swing=pace · wrist twist=spin     TV: arrows=move · OK=Player 2 · UP=arena · DOWN=country · MENU=AI"

func _update_trail() -> void:
	if not rally_active: return
	trail_points.append(ball_position)
	if trail_points.size() > 13: trail_points.pop_front()
	trail_mesh.clear_surfaces()
	if trail_points.size() < 2: return
	trail_mesh.surface_begin(Mesh.PRIMITIVE_LINE_STRIP)
	for i in range(trail_points.size()):
		var strength := float(i) / float(trail_points.size())
		trail_mesh.surface_set_color(Color(1.0, 0.56 + 0.35 * strength, 0.28, 0.12 + 0.78 * strength))
		trail_mesh.surface_add_vertex(trail_points[i])
	trail_mesh.surface_end()

func _unhandled_key_input(event: InputEvent) -> void:
	if not event is InputEventKey or not event.pressed or event.echo: return
	match event.keycode:
		KEY_UP:
			stadium_index = (stadium_index + 1) % STADIUMS.size()
			_apply_stadium()
			banner = "NOW PLAYING  ·  %s" % STADIUMS[stadium_index]
		KEY_DOWN:
			matchup_index = (matchup_index + 1) % MATCHUPS.size()
			near_score = 0
			far_score = 0
			_reset_ball("near")
			banner = "COUNTRY MATCH  ·  %s vs %s" % [MATCHUPS[matchup_index][0], MATCHUPS[matchup_index][1]]
		KEY_MENU:
			two_player = not two_player
			near_score = 0
			far_score = 0
			point_pause = 0.0
			_reset_ball("near")
			banner = "PHONE vs AI" if not two_player else "PHONE P1  ·  TV REMOTE P2"
		KEY_SPACE, KEY_ENTER, KEY_KP_ENTER:
			if two_player:
				_try_hit("far", 0.62, aim_keyboard, side_spin_keyboard, top_spin_keyboard)
			else:
				_try_hit("near", 0.62, aim_keyboard, side_spin_keyboard, top_spin_keyboard)
		KEY_LEFT:
			aim_keyboard = maxf(-1.0, aim_keyboard - 0.18)
			if two_player: far_x = maxf(-1.25, far_x - 0.20)
		KEY_RIGHT:
			aim_keyboard = minf(1.0, aim_keyboard + 0.18)
			if two_player: far_x = minf(1.25, far_x + 0.20)
		KEY_J: far_x = maxf(-1.25, far_x - 0.18)
		KEY_L: far_x = minf(1.25, far_x + 0.18)
		KEY_Q: side_spin_keyboard = -1.0
		KEY_E: side_spin_keyboard = 1.0
		KEY_R: top_spin_keyboard = 1.0
		KEY_F: top_spin_keyboard = -1.0
	if near_score >= 11 and near_score - far_score >= 2 or far_score >= 11 and far_score - near_score >= 2:
		if event.keycode in [KEY_SPACE, KEY_ENTER, KEY_KP_ENTER]:
			near_score = 0
			far_score = 0
			point_pause = 0.0
			_reset_ball("near")

func _mat(color: Color, roughness := 0.72, metallic := 0.0) -> StandardMaterial3D:
	var material := StandardMaterial3D.new()
	material.albedo_color = color
	material.roughness = roughness
	material.metallic = metallic
	return material

func _add_box(node_name: String, pos: Vector3, size: Vector3, material: Material) -> MeshInstance3D:
	var mesh := BoxMesh.new()
	mesh.size = size
	var instance := MeshInstance3D.new()
	instance.name = node_name
	instance.mesh = mesh
	instance.material_override = material
	instance.position = pos
	add_child(instance)
	return instance

func _box_mesh(node_name: String, size: Vector3, material: Material) -> MeshInstance3D:
	var mesh := BoxMesh.new()
	mesh.size = size
	var instance := MeshInstance3D.new()
	instance.name = node_name
	instance.mesh = mesh
	instance.material_override = material
	return instance

func _sphere(node_name: String, scale_value: Vector3, material: Material) -> MeshInstance3D:
	var mesh := SphereMesh.new()
	mesh.radius = 1.0
	mesh.height = 2.0
	mesh.radial_segments = 12
	mesh.rings = 8
	var instance := MeshInstance3D.new()
	instance.name = node_name
	instance.mesh = mesh
	instance.material_override = material
	instance.scale = scale_value
	return instance

func _capsule(node_name: String, radius: float, height: float, material: Material) -> MeshInstance3D:
	var mesh := CapsuleMesh.new()
	mesh.radius = radius
	mesh.height = height
	mesh.radial_segments = 10
	mesh.rings = 4
	var instance := MeshInstance3D.new()
	instance.name = node_name
	instance.mesh = mesh
	instance.material_override = material
	return instance

func _cylinder(node_name: String, radius: float, height: float, material: Material) -> MeshInstance3D:
	var mesh := CylinderMesh.new()
	mesh.top_radius = radius
	mesh.bottom_radius = radius
	mesh.height = height
	mesh.radial_segments = 16
	var instance := MeshInstance3D.new()
	instance.name = node_name
	instance.mesh = mesh
	instance.material_override = material
	return instance

func _torus(node_name: String, radius: float, thickness: float, material: Material) -> MeshInstance3D:
	var mesh := TorusMesh.new()
	mesh.inner_radius = radius - thickness
	mesh.outer_radius = radius + thickness
	mesh.rings = 16
	mesh.ring_segments = 8
	var instance := MeshInstance3D.new()
	instance.name = node_name
	instance.mesh = mesh
	instance.material_override = material
	return instance

