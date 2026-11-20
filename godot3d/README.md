# Rally 3D vertical slice

This is the first 3D court prototype for the TV side of Rally. It is a separate Godot project so the existing Java phone controller and APK stay available while the new renderer is developed.

## Open and run

1. Install or extract Godot 4.7.2 or newer.
2. In Godot Project Manager, import this `project.godot` file.
3. Run the project. The scene builds its court, lights, crowd, table, players, rackets, scoreboard, and ball from code, so it has no external art downloads.

## Phone and TV

Keep the existing Rally Android app on the phone, choose **I'm the Racket**, then run this Godot project on the TV or a computer connected to the same Wi-Fi. The prototype sends the same discovery beacon on UDP 48001 and receives the current phone's `H,power,aim,sidespin,topspin` swing packets on UDP 48002. It also accepts the older two-value hit packet.

The 3D match starts as local two-player: the phone plays the near side and the TV remote plays the far side. Press MENU to switch to phone-vs-AI. UP cycles arena lighting/table themes; DOWN starts the next country matchup. The TV UI lists the controls.

## Prototype controls

- Phone: tilt to aim, swing for pace, and twist for spin.
- TV remote: OK to hit as Player 2, left/right to aim and move, UP for arena, DOWN for country matchup, MENU to toggle AI.
- Keyboard: A/D move the phone-side player; arrows move Player 2; Space hits for Player 2 in local two-player or the phone-side player in AI mode; Q/E add sidespin; R/F add topspin.

The ball uses a fixed-size time step per rendered frame, gravity, drag, a Magnus-style spin force, table bounces, net faults, scoring, and a trail. It is a tuning prototype, not yet validated against real table-tennis measurements. The player characters and audience are procedural placeholders; the next art pass should replace them with rigged player models, animation, sound, and original stadium assets. Rendering uses Godot's Compatibility renderer for testing on lower-end hardware.

