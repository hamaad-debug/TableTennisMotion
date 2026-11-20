# Rally — Table Tennis Motion

One Android app runs on both your phone and Android TV. On launch, choose
**I'M THE RACKET** on the phone or **I'M THE TV** on the TV. The phone reads
your swing and aim; the TV shows the court, rally, score, and animated players.
Both devices must be on the same Wi-Fi. Gameplay uses your local network and
does not require a cloud service or subscription.

## Part 1: Put the project on GitHub (once)
1. Go to https://github.com and sign in (or Sign up - free).
2. Click the "+" at the top right -> "New repository".
3. Name: TableTennisMotion. Choose "Public". Click "Create repository".
4. Click "uploading an existing file".
5. Open the unzipped project folder on your computer and drag EVERYTHING inside it
   into the page (app folder, .github folder, build.gradle, settings.gradle, gradle.properties, README.md).
6. Scroll down, click "Commit changes".
IMPORTANT: if the ".github" folder did not upload (hidden folders sometimes don't):
   click "Add file" -> "Create new file", type  .github/workflows/build.yml  as the name
   (typing the slashes makes folders), paste the contents of build.yml, click "Commit changes".

## Part 2: Wait for the build (about 3-6 minutes)
1. Click the "Actions" tab at the top of your repository.
2. If it asks, click "I understand my workflows, go ahead and enable them".
3. Click the newest run ("Build APKs"). Yellow dot = building, green tick = done, red X = failed.
4. If it is red: click it, copy the red error text, and send it to me. I will fix it.

## Part 3: Download the APKs
1. Open the finished (green) run.
2. Scroll to the bottom to "Artifacts".
3. Click "Phone-APK" and "TV-APK" - each downloads a .zip. Unzip them.
   (They contain the same app, just named for each device.)

## Part 4: Install
Phone (Redmi 9 Prime): open the APK file -> allow "Install unknown apps" for your file manager/browser -> Install.
TV (Mi TV 4A): copy TT-TV-Game.apk to a pen drive -> plug into the TV -> open it with a file manager
   (e.g. "File Commander" / Mi "FileManager") -> allow install from unknown sources -> Install.
   Or install "Send Files to TV" on both and send the APK over Wi-Fi.
   The app appears in the TV's apps list as "TT Motion".

## Part 5: Play
1. Open the app on the TV and choose **I'M THE TV**. It shows the TV IP and waits for the phone.
2. Open the app on the phone and choose **I'M THE RACKET**. It should say "TV ready" in a few seconds.
   If not: type the TV IP into the box and tap "Connect to this IP".
3. TV top-left turns green: "Phone: connected".
4. Hold the phone upright (portrait), screen facing you. Swing it like a racket to serve and return.
   - Swing sideways to aim left/right (use "Flip left/right aim" if it feels reversed).
   - Harder swing = deeper shot. Too weak/too soft? Move the Sensitivity slider.
   - You can only hit AFTER the ball has bounced on your side of the table.
5. First to 11 (win by 2) wins. Swing to play again.
No phone nearby? On the TV remote: OK = hit, LEFT/RIGHT = aimed hit.

## Troubleshooting
- Phone can't find TV: both on the SAME Wi-Fi? Some routers have "AP/client isolation" (turn it off). Use the IP box.
- Swings don't register: raise Sensitivity (slide right). "Motion" number on the phone should exceed the threshold when you swing.
- Laggy on TV: normal on Mi TV 4A over weak Wi-Fi; use 5GHz Wi-Fi/near router.
