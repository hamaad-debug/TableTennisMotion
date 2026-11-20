package com.hmaad.tabletennis;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Pure game rules + physics (no Android code). x: -1..1 across table, z: 0 (you) .. 1 (AI), y: height. */
public class GameLogic {
    public interface Sound { void hit(); void win(); void lose(); }

    public static final int SERVE_PLAYER = 0, SERVE_AI = 1, RALLY = 2, POINT_PAUSE = 3, GAME_OVER = 4;
    public static final int PLAYER = 0, AI = 1;
    public static final float G = 1.6f, VY0 = 0.75f, HIT_Y = 0.15f, NET_H = 0.10f, BOUNCE = 0.85f;
    public static final float FLIGHT = (VY0 + (float) Math.sqrt(VY0 * VY0 + 2f * G * HIT_Y)) / G;

    public Sound sound;
    private final ConcurrentLinkedQueue<float[]> hits = new ConcurrentLinkedQueue<float[]>();
    private final Random rnd = new Random();

    public int state;
    public int playerScore, aiScore;
    public float bx, bz, by, bvx, bvz, bvy;
    public int lastHitter = PLAYER, bounces = 0;
    public boolean aiDecided = false;
    public float lastPower = 0.5f, stateTimer = 0f, swingT = 0f, swingDir = 1f, playerX = 0f, oppX = 0f;
    public String message = "";

    public GameLogic() { startServe(); }

    public void queueHit(float power, float aim) {
        hits.add(new float[]{clamp(power, 0f, 1f), clamp(aim, -1f, 1f)});
    }

    static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    private void startServe() {
        bounces = 0; aiDecided = false; bvx = 0; bvy = 0; bvz = 0;
        if (((playerScore + aiScore) / 2) % 2 == 0) {
            state = SERVE_PLAYER; bx = 0f; bz = 0.05f; by = HIT_Y;
            message = "Swing your phone to SERVE";
        } else {
            state = SERVE_AI; stateTimer = 1.3f; bx = 0f; bz = 0.95f; by = HIT_Y;
            message = "AI is serving...";
        }
    }

    private void aiHit() {
        float hz = Math.min(bz, 0.95f);
        float targetZ = 0.12f + rnd.nextFloat() * 0.28f;
        float targetX = (rnd.nextFloat() * 2f - 1f) * 0.8f;
        bz = hz; by = HIT_Y; bvy = VY0;
        bvz = (targetZ - hz) / FLIGHT;
        bvx = (targetX - bx) / FLIGHT;
        lastHitter = AI; bounces = 0; aiDecided = false; state = RALLY; message = "";
        if (sound != null) sound.hit();
    }

    private void playerHit(float power, float aim) {
        if (bz < -0.3f) bz = -0.3f;
        float targetZ = 0.62f + 0.33f * power;
        float targetX = clamp(aim * 0.8f + (rnd.nextFloat() - 0.5f) * 0.1f, -0.9f, 0.9f);
        by = HIT_Y; bvy = VY0;
        bvz = (targetZ - bz) / FLIGHT;
        bvx = (targetX - bx) / FLIGHT;
        lastHitter = PLAYER; bounces = 0; aiDecided = false; state = RALLY; message = "";
        lastPower = power;
        if (sound != null) sound.hit();
    }

    private void handleHit(float power, float aim) {
        swingT = 0.25f; swingDir = aim >= 0f ? 1f : -1f;
        if (state == GAME_OVER) {
            if (stateTimer <= 0f) { playerScore = 0; aiScore = 0; startServe(); }
            return;
        }
        if (state == SERVE_PLAYER) { playerHit(power, aim); return; }
        if (state == RALLY && lastHitter == AI && bounces >= 1 && bvz < 0f && bz < 0.45f && bz > -0.3f) {
            playerHit(power, aim);
        }
    }

    private void awardPoint(int winner) {
        if (winner == PLAYER) playerScore++; else aiScore++;
        bvx = 0; bvy = 0; bvz = 0;
        if (by < 0f) by = 0f;
        boolean over = (playerScore >= 11 || aiScore >= 11) && Math.abs(playerScore - aiScore) >= 2;
        if (over) {
            state = GAME_OVER; stateTimer = 2f;
            message = playerScore > aiScore ? "YOU WIN!  Swing to play again" : "AI WINS.  Swing to play again";
        } else {
            state = POINT_PAUSE; stateTimer = 1.5f;
            message = winner == PLAYER ? "Point for YOU!" : "Point for AI";
        }
        if (sound != null) { if (winner == PLAYER) sound.win(); else sound.lose(); }
    }

    public void update(float dt) {
        float[] h = hits.poll();
        while (h != null) { handleHit(h[0], h[1]); h = hits.poll(); }
        if (swingT > 0f) swingT -= dt;

        float tp = (state == RALLY && lastHitter == AI) ? clamp(bx, -1f, 1f) : 0f;
        playerX += (tp - playerX) * Math.min(1f, dt * 8f);
        float to = (state == RALLY && lastHitter == PLAYER) ? clamp(bx, -1f, 1f) : 0f;
        oppX += (to - oppX) * Math.min(1f, dt * 6f);

        if (state == POINT_PAUSE) { stateTimer -= dt; if (stateTimer <= 0f) startServe(); return; }
        if (state == GAME_OVER) { stateTimer -= dt; return; }
        if (state == SERVE_AI) { stateTimer -= dt; if (stateTimer <= 0f) aiHit(); return; }
        if (state != RALLY) return;

        int receiver = lastHitter == PLAYER ? AI : PLAYER;
        float pz = bz;
        bx += bvx * dt; bz += bvz * dt; by += bvy * dt; bvy -= G * dt;

        if ((pz < 0.5f) != (bz < 0.5f) && by < NET_H) { awardPoint(receiver); return; }

        if (by <= 0f && bvy < 0f) {
            boolean onTable = Math.abs(bx) <= 1f && bz >= 0f && bz <= 1f;
            if (onTable) {
                int side = bz >= 0.5f ? AI : PLAYER;
                if (side != receiver) { awardPoint(receiver); return; }
                bounces++;
                if (bounces >= 2) { awardPoint(lastHitter); return; }
                by = 0f; bvy = -bvy * BOUNCE;
            } else {
                awardPoint(bounces >= 1 ? lastHitter : receiver); return;
            }
        }
        if (bz < -0.7f || bz > 1.7f || Math.abs(bx) > 3f) {
            awardPoint(bounces >= 1 ? lastHitter : receiver); return;
        }

        if (lastHitter == PLAYER && bounces >= 1 && bvz > 0f && bz >= 0.88f && !aiDecided) {
            aiDecided = true;
            float skill = 0.97f - 0.3f * lastPower;
            if (rnd.nextFloat() < skill) aiHit();
        }
    }
}
