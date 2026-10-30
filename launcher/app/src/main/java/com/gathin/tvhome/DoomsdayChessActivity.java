package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interactive 3D-perspective Avengers: Doomsday Chess game launched from the Reel Home screen.
 * Features all 26 sculpted Marvel pieces, the opening Magneto defection & Loki arrival sequence,
 * Doctor Doom's reactive commentary, and a full chess engine with AI opponent.
 */
public final class DoomsdayChessActivity extends Activity {

    private ChessView chessView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        chessView = new ChessView(this);
        setContentView(chessView);
        chessView.requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chessView != null) {
            chessView.cleanup();
        }
    }

    static final class ChessView extends View {
        private final ChessEngine engine = new ChessEngine();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Map<String, Bitmap> sprites = new HashMap<>();
        private Bitmap doomBg;
        private Typeface outfitBold;
        private Typeface outfitRegular;

        private int cursorR = 6;
        private int cursorC = 4;
        private int selectedR = -1;
        private int selectedC = -1;
        private List<ChessEngine.Move> legalForSelected = Collections.emptyList();

        // Opening story state
        private boolean openingSequenceActive = true;
        private long openingStartMs;
        private static final long OPENING_DURATION_MS = 3400L;

        // Move animation state
        private boolean moveAnimating = false;
        private String animSprite;
        private int animFromR, animFromC, animToR, animToC;
        private float animProgress = 0f;
        private ValueAnimator moveAnimator;
        private boolean aiPending = false;

        // Reusable drawing primitives
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint spritePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubbleBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path quadPath = new Path();
        private final RectF dstRect = new RectF();
        private final RectF bubbleRect = new RectF();

        private final Runnable frameTicker = new Runnable() {
            @Override
            public void run() {
                invalidate();
                handler.postDelayed(this, 16L);
            }
        };

        ChessView(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            loadAssets(context);
            startNewMatch(true);
            handler.post(frameTicker);
        }

        void cleanup() {
            handler.removeCallbacksAndMessages(null);
            if (moveAnimator != null) moveAnimator.cancel();
        }

        private void loadAssets(Context context) {
            AssetManager am = context.getAssets();
            try {
                outfitRegular = Typeface.createFromAsset(am, "fonts/Outfit-Regular.ttf");
                outfitBold = Typeface.create(outfitRegular, Typeface.BOLD);
            } catch (Exception e) {
                outfitRegular = Typeface.SANS_SERIF;
                outfitBold = Typeface.DEFAULT_BOLD;
            }
            doomBg = cropAndFeatherDoom(decodeAsset(am, "chess/doom_bg.png"));
            String[] names = {
                "doom_king", "wanda_queen", "hulk_rook", "sentinel_rook",
                "spidey_knight", "namor_knight", "strange_bishop", "magneto_black", "doombot_pawn",
                "reed_king", "sue_queen", "torch_knight", "thing_pawn",
                "profx_bishop", "cyclops_rook", "gambit_pawn", "nightcrawler_pawn", "beast_pawn",
                "magneto_white", "steve_rook", "thor_knight", "panther_pawn",
                "shangchi_pawn", "bucky_pawn", "antman_pawn", "loki_bishop"
            };
            for (String name : names) {
                Bitmap b = decodeAsset(am, "chess/" + name + ".png");
                if (b != null) sprites.put(name, b);
            }
        }

        private static Bitmap decodeAsset(AssetManager am, String path) {
            try (InputStream is = am.open(path)) {
                return BitmapFactory.decodeStream(is);
            } catch (IOException e) {
                return null;
            }
        }

        private static Bitmap cropAndFeatherDoom(Bitmap raw) {
            if (raw == null || raw.getWidth() < 50 || raw.getHeight() < 50) return raw;
            int rw = raw.getWidth();
            int rh = raw.getHeight();
            int x0 = Math.round(rw * 0.115f);
            int y0 = Math.round(rh * 0.125f);
            int cw = Math.round(rw * 0.77f);
            int ch = Math.round(rh * 0.60f);
            Bitmap cropped = Bitmap.createBitmap(raw, x0, y0, cw, ch);
            Bitmap out = cropped.copy(Bitmap.Config.ARGB_8888, true);
            int[] pixels = new int[cw * ch];
            out.getPixels(pixels, 0, cw, 0, 0, cw, ch);
            for (int y = 0; y < ch; y++) {
                float fy = Math.min(y / (ch * 0.18f), (ch - 1 - y) / (ch * 0.28f));
                fy = Math.max(0f, Math.min(1f, fy));
                int rowOffset = y * cw;
                for (int x = 0; x < cw; x++) {
                    float fx = Math.min(x / (cw * 0.18f), (cw - 1 - x) / (cw * 0.18f));
                    fx = Math.max(0f, Math.min(1f, fx));
                    float f = fx * fy;
                    if (f < 1f) {
                        int c = pixels[rowOffset + x];
                        int a = Math.round(((c >>> 24) & 0xFF) * f);
                        int r = Math.round(((c >> 16) & 0xFF) * f);
                        int g = Math.round(((c >> 8) & 0xFF) * f);
                        int b = Math.round((c & 0xFF) * f);
                        pixels[rowOffset + x] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }
            }
            out.setPixels(pixels, 0, cw, 0, 0, cw, ch);
            return out;
        }

        void startNewMatch(boolean playOpeningStory) {
            if (moveAnimator != null) moveAnimator.cancel();
            moveAnimating = false;
            aiPending = false;
            selectedR = -1;
            selectedC = -1;
            legalForSelected = Collections.emptyList();
            cursorR = 6;
            cursorC = 4;
            if (playOpeningStory) {
                engine.resetPreDefection();
                openingSequenceActive = true;
                openingStartMs = SystemClock.uptimeMillis();
            } else {
                engine.reset();
                openingSequenceActive = false;
            }
            invalidate();
        }

        /**
         * Maps fractional board coordinates (col in [0..8], row in [0..8]) to screen pixels (x, y)
         * using a perspective frustum that matches the Doomsday Chess reel.
         */
        private PointF project(float col, float row, int w, int h) {
            float u = row / 8f; // 0 at top (rank 8), 1 at bottom (rank 1)
            // Perspective foreshortening along depth (keep finite for negative row borders)
            float depth = u >= 0f ? (float) Math.pow(u, 1.16f) : -(float) Math.pow(-u, 1.16f);
            float topY = h * 0.30f;
            float bottomY = h * 0.89f;
            float y = topY + (bottomY - topY) * depth;

            float topWidth = w * 0.45f;
            float bottomWidth = w * 0.76f;
            float rowWidth = topWidth + (bottomWidth - topWidth) * depth;
            float leftX = (w - rowWidth) * 0.5f;
            float x = leftX + (col / 8f) * rowWidth;
            return new PointF(x, y);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            long now = SystemClock.uptimeMillis();

            // 1. Deep Latverian backdrop gradient
            bgPaint.setShader(new RadialGradient(
                    w * 0.5f, h * 0.24f, Math.max(w, h) * 0.75f,
                    new int[]{0xFF132A1B, 0xFF07110B, 0xFF020403},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, bgPaint);

            // 2. Doctor Doom looming behind the board
            if (doomBg != null) {
                float breathe = (float) Math.sin(now * 0.0022) * (h * 0.006f);
                float doomH = h * 0.42f;
                float doomW = doomH * ((float) doomBg.getWidth() / Math.max(1, doomBg.getHeight()));
                float left = (w - doomW) * 0.5f;
                float top = h * 0.01f + breathe;
                dstRect.set(left, top, left + doomW, top + doomH);
                spritePaint.setAlpha(240);
                canvas.drawBitmap(doomBg, null, dstRect, spritePaint);
                spritePaint.setAlpha(255);
            }

            // 3. Check opening story progression
            float openingElapsed = 0f;
            if (openingSequenceActive) {
                openingElapsed = now - openingStartMs;
                if (openingElapsed >= OPENING_DURATION_MS) {
                    openingSequenceActive = false;
                    engine.completeOpeningDefection();
                } else if (openingElapsed > 2200f) {
                    engine.doomQuote = "Wait, is that Loki?!";
                } else if (openingElapsed > 600f) {
                    engine.doomQuote = "Magneto knows where true power lies.";
                }
            }

            // 4. Draw 3D Board Plinth & Outer Rim
            PointF tl = project(-0.22f, -0.22f, w, h);
            PointF tr = project(8.22f, -0.22f, w, h);
            PointF br = project(8.28f, 8.25f, w, h);
            PointF bl = project(-0.28f, 8.25f, w, h);
            float plinthDepth = h * 0.036f;

            // Front 3D edge of plinth
            quadPath.reset();
            quadPath.moveTo(bl.x, bl.y);
            quadPath.lineTo(br.x, br.y);
            quadPath.lineTo(br.x, br.y + plinthDepth);
            quadPath.lineTo(bl.x, bl.y + plinthDepth);
            quadPath.close();
            boardPaint.setShader(new LinearGradient(0, bl.y, 0, bl.y + plinthDepth,
                    0xFF1A291E, 0xFF090F0B, Shader.TileMode.CLAMP));
            canvas.drawPath(quadPath, boardPaint);

            // Board frame top surface
            quadPath.reset();
            quadPath.moveTo(tl.x, tl.y);
            quadPath.lineTo(tr.x, tr.y);
            quadPath.lineTo(br.x, br.y);
            quadPath.lineTo(bl.x, bl.y);
            quadPath.close();
            boardPaint.setShader(new LinearGradient(0, tl.y, 0, br.y,
                    0xFF243829, 0xFF111C15, Shader.TileMode.CLAMP));
            canvas.drawPath(quadPath, boardPaint);
            boardPaint.setShader(null);

            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(2f);
            linePaint.setColor(0x664ADE80);
            canvas.drawPath(quadPath, linePaint);

            // 5. Draw 8x8 Perspective Squares
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    PointF p00 = project(c, r, w, h);
                    PointF p10 = project(c + 1, r, w, h);
                    PointF p11 = project(c + 1, r + 1, w, h);
                    PointF p01 = project(c, r + 1, w, h);

                    quadPath.reset();
                    quadPath.moveTo(p00.x, p00.y);
                    quadPath.lineTo(p10.x, p10.y);
                    quadPath.lineTo(p11.x, p11.y);
                    quadPath.lineTo(p01.x, p01.y);
                    quadPath.close();

                    boolean light = ((r + c) & 1) == 0;
                    boardPaint.setStyle(Paint.Style.FILL);
                    boardPaint.setColor(light ? 0xFFD8E0C9 : 0xFF1B2B1F);
                    canvas.drawPath(quadPath, boardPaint);

                    // Last move highlight
                    if ((r == engine.lastFromR && c == engine.lastFromC)
                            || (r == engine.lastToR && c == engine.lastToC)) {
                        boardPaint.setColor(0x55F59E0B);
                        canvas.drawPath(quadPath, boardPaint);
                    }

                    // Selected square highlight
                    if (r == selectedR && c == selectedC) {
                        boardPaint.setColor(0x7722C55E);
                        canvas.drawPath(quadPath, boardPaint);
                    }

                    // Cursor highlight
                    if (!openingSequenceActive && r == cursorR && c == cursorC) {
                        boardPaint.setColor(0x4438BDF8);
                        canvas.drawPath(quadPath, boardPaint);
                        linePaint.setStrokeWidth(3.5f);
                        linePaint.setColor(0xFF38BDF8);
                        canvas.drawPath(quadPath, linePaint);
                    }
                }
            }

            // 6. Legal Move Indicators
            for (ChessEngine.Move m : legalForSelected) {
                PointF center = project(m.toC + 0.5f, m.toR + 0.5f, w, h);
                float scale = 0.65f + 0.35f * (m.toR / 7f);
                if (engine.board[m.toR][m.toC] != null) {
                    linePaint.setStyle(Paint.Style.STROKE);
                    linePaint.setStrokeWidth(3.5f * scale);
                    linePaint.setColor(0xEEEF4444);
                    canvas.drawCircle(center.x, center.y, w * 0.022f * scale, linePaint);
                } else {
                    glowPaint.setStyle(Paint.Style.FILL);
                    glowPaint.setColor(0xCC22C55E);
                    canvas.drawCircle(center.x, center.y, w * 0.011f * scale, glowPaint);
                }
            }

            // 7. Render Sculpted Pieces (Back-to-Front by Row 0..7)
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (moveAnimating && r == animFromR && c == animFromC) continue;
                    // During opening sequence, Magneto on f1 (r=7, c=5) is drawn separately while flying
                    if (openingSequenceActive && openingElapsed >= 900f && r == 7 && c == 5) continue;

                    ChessEngine.Piece piece = engine.board[r][c];
                    if (piece == null) continue;
                    drawPieceAt(canvas, piece.sprite, c + 0.5f, r + 0.5f, 0f, w, h,
                            !openingSequenceActive && r == cursorR && c == cursorC);
                }
            }

            // 8. Opening Sequence: Magneto Defection (f1 -> f8) & Loki Arrival (f1)
            if (openingSequenceActive && openingElapsed >= 900f) {
                if (openingElapsed < 2200f) {
                    float t = Math.min(1f, (openingElapsed - 900f) / 1200f);
                    // Smooth ease in-out
                    float s = t * t * (3f - 2f * t);
                    float col = 5.5f;
                    float row = 7.5f + (0.5f - 7.5f) * s;
                    float hop = (float) Math.sin(s * Math.PI) * (h * 0.14f);
                    String sprite = s < 0.55f ? "magneto_white" : "magneto_black";
                    PointF pos = project(col, row, w, h);
                    glowPaint.setStyle(Paint.Style.STROKE);
                    glowPaint.setStrokeWidth(4f);
                    glowPaint.setColor(0xCCEF4444);
                    canvas.drawCircle(pos.x, pos.y - hop - h * 0.05f, h * 0.055f, glowPaint);
                    drawPieceAt(canvas, sprite, col, row, hop, w, h, true);
                } else {
                    // Magneto landed at f8 (r=0, c=5)
                    drawPieceAt(canvas, "magneto_black", 5.5f, 0.5f, 0f, w, h, false);
                    // Loki materializing at f1 (r=7, c=5) with green magic aura
                    float lt = Math.min(1f, (openingElapsed - 2200f) / 900f);
                    PointF lokiPos = project(5.5f, 7.5f, w, h);
                    glowPaint.setStyle(Paint.Style.FILL);
                    int alpha = (int) ((1f - lt * 0.5f) * 190);
                    glowPaint.setColor(Color.argb(alpha, 34, 197, 94));
                    canvas.drawCircle(lokiPos.x, lokiPos.y - h * 0.05f, h * 0.065f * (1.2f - 0.3f * lt), glowPaint);
                    drawPieceAt(canvas, "loki_bishop", 5.5f, 7.5f, (1f - lt) * h * 0.06f, w, h, true);
                }
            }

            // 9. Animated Moving Piece
            if (moveAnimating && animSprite != null) {
                float col = (animFromC + 0.5f) + (animToC - animFromC) * animProgress;
                float row = (animFromR + 0.5f) + (animToR - animFromR) * animProgress;
                float hop = (float) Math.sin(animProgress * Math.PI) * (h * 0.08f);
                drawPieceAt(canvas, animSprite, col, row, hop, w, h, true);
            }

            // 10. Doctor Doom Speech Bubble
            drawDoomSpeechBubble(canvas, w, h);

            // 11. Top-Left & Bottom HUD
            drawHud(canvas, w, h);
        }

        private void drawPieceAt(Canvas canvas, String spriteName, float boardCol, float boardRow,
                                 float hopPx, int w, int h, boolean focused) {
            Bitmap bmp = sprites.get(spriteName);
            if (bmp == null) return;
            PointF p = project(boardCol, boardRow, w, h);
            float depthScale = 0.64f + 0.42f * (boardRow / 8f);
            if (focused) depthScale *= 1.08f;

            float pieceH = h * 0.138f * depthScale;
            float pieceW = pieceH * ((float) bmp.getWidth() / Math.max(1, bmp.getHeight()));

            // Elliptical ground shadow
            shadowPaint.setColor(0x88000000);
            float sw = pieceW * 0.40f;
            float sh = sw * 0.32f;
            dstRect.set(p.x - sw, p.y - sh, p.x + sw, p.y + sh);
            canvas.drawOval(dstRect, shadowPaint);

            // Draw upright sculpted figure anchored near bottom of sprite
            float left = p.x - pieceW * 0.5f;
            float bottom = p.y + pieceH * 0.08f - hopPx;
            float top = bottom - pieceH;
            dstRect.set(left, top, left + pieceW, bottom);
            canvas.drawBitmap(bmp, null, dstRect, spritePaint);
        }

        private void drawDoomSpeechBubble(Canvas canvas, int w, int h) {
            String quote = engine.doomQuote;
            if (quote == null || quote.isEmpty()) return;

            textPaint.setTypeface(outfitBold);
            textPaint.setTextSize(h * 0.024f);
            float textW = textPaint.measureText(quote);
            float padX = w * 0.018f;
            float padY = h * 0.016f;
            float bw = textW + padX * 2f;
            float bh = h * 0.064f;
            float bx = Math.min(w * 0.94f - bw, w * 0.57f);
            float by = h * 0.08f;

            bubbleRect.set(bx, by, bx + bw, by + bh);
            bubblePaint.setColor(0xE60D1B12);
            canvas.drawRoundRect(bubbleRect, 18f, 18f, bubblePaint);

            bubbleBorderPaint.setStyle(Paint.Style.STROKE);
            bubbleBorderPaint.setStrokeWidth(2.5f);
            bubbleBorderPaint.setColor(0xFF4ADE80);
            canvas.drawRoundRect(bubbleRect, 18f, 18f, bubbleBorderPaint);

            textPaint.setColor(0xFFF0FDF4);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float ty = bubbleRect.centerY() - (fm.ascent + fm.descent) * 0.5f;
            canvas.drawText(quote, bx + padX, ty, textPaint);
        }

        private void drawHud(Canvas canvas, int w, int h) {
            float left = w * 0.04f;
            textPaint.setTypeface(outfitBold);
            textPaint.setTextSize(h * 0.036f);
            textPaint.setColor(0xFFF8FAFC);
            canvas.drawText("AVENGERS: DOOMSDAY CHESS", left, h * 0.075f, textPaint);

            textPaint.setTypeface(outfitRegular);
            textPaint.setTextSize(h * 0.022f);
            String status;
            if (openingSequenceActive) {
                status = "OPENING GAMBIT — MAGNETO DEFECTS TO DOOM";
                textPaint.setColor(0xFFFBBF24);
            } else if (engine.gameOver) {
                status = engine.winnerText + "  •  Press MENU for New Match";
                textPaint.setColor(0xFF4ADE80);
            } else if (engine.turn == ChessEngine.WHITE) {
                status = "YOUR TURN (AVENGERS / X-MEN / FANTASTIC FOUR)";
                textPaint.setColor(0xFF38BDF8);
            } else {
                status = "DOCTOR DOOM IS PLOTTING HIS MOVE...";
                textPaint.setColor(0xFFF87171);
            }
            canvas.drawText(status, left, h * 0.112f, textPaint);

            // Focused square & character inspector
            ChessEngine.Piece focused = engine.board[cursorR][cursorC];
            String squareName = "" + (char) ('A' + cursorC) + (8 - cursorR);
            String info = "Square " + squareName;
            if (focused != null) {
                String side = focused.color == ChessEngine.WHITE ? "Hero" : "Doom Cabal";
                info += "  •  " + focused.heroName + " (" + side + " " + roleName(focused.role) + ")";
            }
            textPaint.setColor(0xCCFFFFFF);
            textPaint.setTextSize(h * 0.021f);
            canvas.drawText(info, left, h * 0.146f, textPaint);

            // Bottom controls bar
            textPaint.setColor(0x99E2E8F0);
            textPaint.setTextSize(h * 0.020f);
            String controls = "D-Pad: Move Cursor   •   OK: Select / Move Piece   •   MENU / PLAY: Restart Match   •   BACK: Return Home";
            float cw = textPaint.measureText(controls);
            canvas.drawText(controls, (w - cw) * 0.5f, h * 0.962f, textPaint);
        }

        private static String roleName(int role) {
            switch (role) {
                case ChessEngine.KING: return "King";
                case ChessEngine.QUEEN: return "Queen";
                case ChessEngine.ROOK: return "Rook";
                case ChessEngine.BISHOP: return "Bishop";
                case ChessEngine.KNIGHT: return "Knight";
                default: return "Pawn";
            }
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                playSoundEffect(SoundEffectConstants.CLICK);
                startNewMatch(true);
                return true;
            }
            if (openingSequenceActive || moveAnimating || aiPending) {
                return super.onKeyDown(keyCode, event);
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (cursorR > 0) {
                        cursorR--;
                        playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (cursorR < 7) {
                        cursorR++;
                        playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (cursorC > 0) {
                        cursorC--;
                        playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (cursorC < 7) {
                        cursorC++;
                        playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    handleSelectAtCursor();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (selectedR != -1) {
                        selectedR = -1;
                        selectedC = -1;
                        legalForSelected = Collections.emptyList();
                        return true;
                    }
                    break;
            }
            return super.onKeyDown(keyCode, event);
        }

        private void handleSelectAtCursor() {
            if (engine.gameOver) {
                startNewMatch(false);
                return;
            }
            if (engine.turn != ChessEngine.WHITE) return;

            // Check if cursor is on a legal move for currently selected piece
            if (selectedR != -1) {
                for (ChessEngine.Move m : legalForSelected) {
                    if (m.toR == cursorR && m.toC == cursorC) {
                        playSoundEffect(SoundEffectConstants.CLICK);
                        animateAndApplyMove(m, true);
                        return;
                    }
                }
            }

            // Otherwise select a White piece at cursor
            ChessEngine.Piece p = engine.board[cursorR][cursorC];
            if (p != null && p.color == ChessEngine.WHITE) {
                selectedR = cursorR;
                selectedC = cursorC;
                legalForSelected = engine.legalMovesFor(cursorR, cursorC);
                playSoundEffect(SoundEffectConstants.CLICK);
            } else {
                selectedR = -1;
                selectedC = -1;
                legalForSelected = Collections.emptyList();
            }
        }

        private void animateAndApplyMove(ChessEngine.Move move, boolean triggerAiAfter) {
            ChessEngine.Piece moving = engine.board[move.fromR][move.fromC];
            if (moving == null) return;
            selectedR = -1;
            selectedC = -1;
            legalForSelected = Collections.emptyList();

            moveAnimating = true;
            animSprite = moving.sprite;
            animFromR = move.fromR;
            animFromC = move.fromC;
            animToR = move.toR;
            animToC = move.toC;
            animProgress = 0f;

            if (moveAnimator != null) moveAnimator.cancel();
            moveAnimator = ValueAnimator.ofFloat(0f, 1f);
            moveAnimator.setDuration(280L);
            moveAnimator.setInterpolator(new DecelerateInterpolator());
            moveAnimator.addUpdateListener(a -> animProgress = (float) a.getAnimatedValue());
            moveAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    moveAnimating = false;
                    engine.makeMove(move);
                    cursorR = move.toR;
                    cursorC = move.toC;
                    if (triggerAiAfter && !engine.gameOver && engine.turn == ChessEngine.BLACK) {
                        aiPending = true;
                        handler.postDelayed(() -> {
                            ChessEngine.Move aiMove = engine.pickAiMove();
                            aiPending = false;
                            if (aiMove != null) {
                                animateAndApplyMove(aiMove, false);
                            }
                        }, 550L);
                    }
                }
            });
            moveAnimator.start();
        }
    }
}
