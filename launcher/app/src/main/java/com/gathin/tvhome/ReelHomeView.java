package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Full-screen 4-section vertical snap Home view implementing the @ikevinadams Instagram Reel
 * and Google Doc design spec, while preserving user app toggling and wallpaper switching.
 */
public final class ReelHomeView extends View {

    interface HostCallbacks {
        void onOpenAppLibrary();
        void onOpenWifiSettings();
        void onOpenSystemSettings();
        void onOpenLauncherSettings();
        void onSwitchThemeRequested();
        Bitmap getCurrentWallpaper();
        void onWallpaperChosen(String fileName);
    }

    private final Context ctx;
    private final HostCallbacks host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final float density;

    private Typeface outfitRegular;
    private Typeface outfitBold;

    // Section navigation (0 = Hero Home, 1 = My Favourites, 2 = Mood Roulette, 3 = Doomsday Chess)
    private int currentSection = 0;
    private float cameraY = 0f;
    private ValueAnimator sectionAnimator;

    // Section 0: Hero Home + Floating White-Logo Dock
    private Bitmap defaultDoomWallpaper;
    private Bitmap customWallpaperOverride;
    private boolean topBarFocused = false;
    private int topBarIndex = 1; // 0 = Wi-Fi, 1 = Toggle Apps, 2 = Wallpapers, 3 = Settings
    private int dockIndex = 0;
    private int taglineIndex = 0;
    private String weatherText = "26°C  Clear · New Delhi";
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("h:mm", Locale.US);

    private static final String[] TAGLINES = {
        "Pick something before your food gets cold",
        "One episode before bed, famous last words",
        "You've been scrolling longer than the movie",
        "Your watchlist is judging you right now",
        "Comfort show for the 47th time? Respect.",
        "Doom awaits, or just watch The Office again"
    };

    static final class DockItem {
        final String pkg;
        final String label;
        final Bitmap whiteLogo;
        final Drawable fallbackDrawable;
        final int glowColor;
        final int adapterPos;
        final boolean isChess;

        DockItem(String pkg, String label, Bitmap whiteLogo, Drawable fallbackDrawable,
                 int glowColor, int adapterPos, boolean isChess) {
            this.pkg = pkg;
            this.label = label;
            this.whiteLogo = whiteLogo;
            this.fallbackDrawable = fallbackDrawable;
            this.glowColor = glowColor;
            this.adapterPos = adapterPos;
            this.isChess = isChess;
        }
    }

    private final List<DockItem> dockItems = new ArrayList<>();
    private final Map<String, Bitmap> logoBitmaps = new HashMap<>();
    private int currentGlowColor = 0xFF00B4D8;
    private ValueAnimator glowAnimator;

    // Section 1: "My Favourites" 7-Character Lineup
    private Bitmap favBw;
    private final Bitmap[] favColor = new Bitmap[7];
    private int favIndex = 0;
    private int prevFavIndex = 0;
    private float favCrossfade = 1f;
    private ValueAnimator favAnimator;
    private long reverseFlashFocusStartMs = 0L;
    private long spideyFocusStartMs = 0L;

    private static final String[] FAV_CHARACTERS = {
        "Patrick Jane", "Sheldon Cooper", "JD", "Walter White",
        "Reverse-Flash", "Spider-Man", "Barney Stinson"
    };
    private static final String[] FAV_SHOWS = {
        "The Mentalist", "The Big Bang Theory", "Master", "Breaking Bad",
        "The Flash", "Spider-Man: No Way Home", "How I Met Your Mother"
    };
    private static final String[] FAV_PLATFORMS = {
        "Prime Video", "Netflix", "Prime Video", "Netflix",
        "JioHotstar", "SonyLIV", "JioHotstar"
    };
    private static final String[] FAV_PACKAGES = {
        "com.amazon.firebat", "com.netflix.ninja", "com.amazon.firebat", "com.netflix.ninja",
        "in.startv.hotstar", "com.onemainstream.sonyliv.android", "in.startv.hotstar"
    };
    private static final String[] FAV_DEEP_LINKS = {
        "", "70143830", "", "70143836",
        "", "", ""
    };
    private static final int[] FAV_BADGE_COLORS = {
        0xFF00A8E1, 0xFFE50914, 0xFF00A8E1, 0xFFE50914,
        0xFF2563EB, 0xFF7C3AED, 0xFF2563EB
    };
    private static final float[] FAV_HEAD_X = {
        0.215f, 0.325f, 0.415f, 0.480f, 0.575f, 0.650f, 0.720f
    };

    // Section 2: "Don't have time to browse?" Mood Cards & Slot-Machine Poster Reel
    private int moodIndex = 0;
    private int sec2FocusRow = 0; // 0 = Mood Card / Reel, 1 = Bottom Pills ("More apps" / "Wallpapers" / "Change Theme")
    private int sec2PillIndex = 0; // 0 = More apps, 1 = Wallpapers, 2 = Change Theme
    private float cardDismissProgress = 0f;
    private boolean cardDismissing = false;
    private boolean reelMode = false;
    private boolean reelSpinning = false;
    private float reelScrollPx = 0f;
    private int reelWinnerIdx = 0;
    private String lastWinnerKey = "";
    private long reelLandedMs = 0L;
    private ValueAnimator reelAnimator;
    private final List<PosterEntry> activeReelList = new ArrayList<>();
    private final Map<String, Bitmap> posterBitmaps = new HashMap<>();
    private final Random random = new Random();

    static final class PosterEntry {
        final String key;
        final String title;
        final String platform;
        final String pkg;
        final String deepLink;

        PosterEntry(String key, String title, String platform, String pkg, String deepLink) {
            this.key = key;
            this.title = title;
            this.platform = platform;
            this.pkg = pkg;
            this.deepLink = deepLink;
        }
    }

    static final class MoodCard {
        final String title;
        final String subtitle;
        final int colorTop;
        final int colorBottom;
        final PosterEntry[] items;

        MoodCard(String title, String subtitle, int colorTop, int colorBottom, PosterEntry[] items) {
            this.title = title;
            this.subtitle = subtitle;
            this.colorTop = colorTop;
            this.colorBottom = colorBottom;
            this.items = items;
        }
    }

    private MoodCard[] moodCards;

    // Section 3: Doomsday Chess Interactive Preview
    private final ChessEngine previewChess = new ChessEngine();
    private Bitmap doomBg;
    private final Map<String, Bitmap> chessSprites = new HashMap<>();

    // Drawing primitives
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Rect srcRect = new Rect();
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();

    private long okDownTimeMs = 0L;
    private boolean okLongHandled = false;

    private final Runnable animationTick = new Runnable() {
        @Override
        public void run() {
            boolean needNext = false;
            long now = SystemClock.uptimeMillis();
            if (currentSection == 1 && (favIndex == 4 && now - reverseFlashFocusStartMs < 1100L
                    || favIndex == 5 && now - spideyFocusStartMs < 1400L
                    || favIndex == 0)) {
                needNext = true;
            }
            if (currentSection == 2 && reelMode && !reelSpinning && reelLandedMs > 0L) {
                long elapsed = now - reelLandedMs;
                if (elapsed >= 3000L) {
                    reelLandedMs = 0L;
                    if (!activeReelList.isEmpty() && reelWinnerIdx < activeReelList.size()) {
                        PosterEntry winner = activeReelList.get(reelWinnerIdx);
                        launchContent(winner.pkg, winner.title, winner.deepLink);
                    }
                } else {
                    needNext = true;
                }
            }
            if (currentSection == 3) {
                needNext = true;
            }
            if (needNext) {
                invalidate();
                handler.postDelayed(this, 16L);
            }
        }
    };

    ReelHomeView(Context context, HostCallbacks hostCallbacks) {
        super(context);
        this.ctx = context;
        this.host = hostCallbacks;
        this.density = context.getResources().getDisplayMetrics().density;
        setFocusable(true);
        setFocusableInTouchMode(true);
        loadAssets();
        initMoods();
        fetchWeatherAsync();
    }

    private void loadAssets() {
        AssetManager am = ctx.getAssets();
        try {
            outfitRegular = Typeface.createFromAsset(am, "fonts/Outfit-Regular.ttf");
            outfitBold = Typeface.create(outfitRegular, Typeface.BOLD);
        } catch (Exception e) {
            outfitRegular = Typeface.SANS_SERIF;
            outfitBold = Typeface.DEFAULT_BOLD;
        }
        defaultDoomWallpaper = decodeAsset(am, "reel/doom_wallpaper.jpg");
        customWallpaperOverride = defaultDoomWallpaper;
        String[] logos = {"tivimate", "playfy", "youtube", "netflix", "hotstar", "prime", "appletv", "chess"};
        for (String l : logos) {
            Bitmap b = decodeAsset(am, "logos/" + l + ".png");
            if (b != null) logoBitmaps.put(l, b);
        }
        favBw = decodeAsset(am, "favs/fav_bw.jpg");
        for (int i = 0; i < 7; i++) {
            favColor[i] = decodeAsset(am, "favs/fav_" + i + ".jpg");
        }
        String[] posters = {
            "p_office", "p_friends", "p_twohalf", "p_tbbt", "p_himym", "p_b99",
            "p_flash", "p_spidey", "p_endgame", "p_infinity", "p_zsjl", "p_bvs",
            "p_thebatman", "p_tdkr", "p_tdk", "p_master", "p_leo", "p_bigil",
            "p_beast", "p_varisu", "p_bb", "p_bcs", "p_mentalist", "p_sherlock", "p_inception",
            "p_cricket1", "p_cricket2", "p_f1_1", "p_f1_2", "p_music1", "p_music2"
        };
        for (String p : posters) {
            Bitmap b = decodeAsset(am, "posters/" + p + ".jpg");
            if (b != null) posterBitmaps.put(p, b);
        }
        doomBg = cropAndFeatherDoom(decodeAsset(am, "chess/doom_bg.png"));
        String[] sprites = {
            "doom_king", "wanda_queen", "hulk_rook", "sentinel_rook",
            "spidey_knight", "namor_knight", "strange_bishop", "magneto_black", "doombot_pawn",
            "reed_king", "sue_queen", "torch_knight", "thing_pawn",
            "profx_bishop", "cyclops_rook", "gambit_pawn", "nightcrawler_pawn", "beast_pawn",
            "magneto_white", "steve_rook", "thor_knight", "panther_pawn",
            "shangchi_pawn", "bucky_pawn", "antman_pawn", "loki_bishop"
        };
        for (String s : sprites) {
            Bitmap b = decodeAsset(am, "chess/" + s + ".png");
            if (b != null) chessSprites.put(s, b);
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

    private void initMoods() {
        moodCards = new MoodCard[]{
            new MoodCard("Comfort sitcom", "Zero stress, pure laughs while you eat", 0xFFF59E0B, 0xFFB45309,
                new PosterEntry[]{
                    new PosterEntry("p_office", "The Office", "Prime Video", "com.amazon.firebat", ""),
                    new PosterEntry("p_friends", "Friends", "Netflix", "com.netflix.ninja", "70153404"),
                    new PosterEntry("p_tbbt", "The Big Bang Theory", "Netflix", "com.netflix.ninja", "70143830"),
                    new PosterEntry("p_himym", "How I Met Your Mother", "JioHotstar", "in.startv.hotstar", ""),
                    new PosterEntry("p_b99", "Brooklyn Nine-Nine", "Netflix", "com.netflix.ninja", "70281562"),
                    new PosterEntry("p_twohalf", "Two and a Half Men", "Prime Video", "com.amazon.firebat", "")
                }),
            new MoodCard("Marvel & Capes", "JioHotstar MCU epics & SonyLIV Spider-Verse", 0xFFEF4444, 0xFF991B1B,
                new PosterEntry[]{
                    new PosterEntry("p_endgame", "Avengers: Endgame", "JioHotstar", "in.startv.hotstar", ""),
                    new PosterEntry("p_spidey", "Spider-Man: No Way Home", "SonyLIV", "com.onemainstream.sonyliv.android", ""),
                    new PosterEntry("p_infinity", "Avengers: Infinity War", "JioHotstar", "in.startv.hotstar", ""),
                    new PosterEntry("p_flash", "The Flash", "JioHotstar", "in.startv.hotstar", ""),
                    new PosterEntry("p_zsjl", "Zack Snyder's Justice League", "JioHotstar", "in.startv.hotstar", "")
                }),
            new MoodCard("Cricket & F1 Live", "Live Match Arena, F1 Paddock & Highlights", 0xFF0EA5E9, 0xFF0369A1,
                new PosterEntry[]{
                    new PosterEntry("p_cricket1", "Live Cricket Match Center", "JioHotstar", "in.startv.hotstar", ""),
                    new PosterEntry("p_cricket2", "Champions Trophy & Test Series", "SonyLIV", "com.onemainstream.sonyliv.android", ""),
                    new PosterEntry("p_f1_1", "Formula 1: Drive to Survive", "Netflix", "com.netflix.ninja", "80204890"),
                    new PosterEntry("p_f1_2", "F1 Grand Prix Highlights", "YouTube", "com.amazon.firetv.youtube", "Formula+1+Race+Highlights"),
                    new PosterEntry("p_music1", "Top Bollywood & Arijit Hits", "YouTube", "com.amazon.firetv.youtube", "Arijit+Singh+Top+Hits+4K"),
                    new PosterEntry("p_music2", "Anirudh & Hip-Hop Bass Mix", "YouTube", "com.amazon.firetv.youtube", "Anirudh+Ravichander+Hits+Jukebox")
                }),
            new MoodCard("Gotham after dark", "Vengeance, rain-soaked streets & chaos", 0xFF3B82F6, 0xFF1D4ED8,
                new PosterEntry[]{
                    new PosterEntry("p_tdk", "The Dark Knight", "Prime Video", "com.amazon.firebat", ""),
                    new PosterEntry("p_thebatman", "The Batman", "JioHotstar", "in.startv.hotstar", ""),
                    new PosterEntry("p_tdkr", "The Dark Knight Rises", "Prime Video", "com.amazon.firebat", ""),
                    new PosterEntry("p_bvs", "Batman v Superman", "Prime Video", "com.amazon.firebat", ""),
                    new PosterEntry("p_zsjl", "Zack Snyder's Justice League", "JioHotstar", "in.startv.hotstar", "")
                }),
            new MoodCard("Thalapathy mass", "High-voltage action & Anirudh beats", 0xFFEC4899, 0xFF9D174D,
                new PosterEntry[]{
                    new PosterEntry("p_master", "Master", "Prime Video", "com.amazon.firebat", ""),
                    new PosterEntry("p_leo", "Leo", "Netflix", "com.netflix.ninja", "81639319"),
                    new PosterEntry("p_bigil", "Bigil", "Prime Video", "com.amazon.firebat", ""),
                    new PosterEntry("p_beast", "Beast", "Netflix", "com.netflix.ninja", "81508241"),
                    new PosterEntry("p_varisu", "Varisu", "Prime Video", "com.amazon.firebat", "")
                }),
            new MoodCard("Mind games", "Outsmarting everyone in the room", 0xFF10B981, 0xFF047857,
                new PosterEntry[]{
                    new PosterEntry("p_bb", "Breaking Bad", "Netflix", "com.netflix.ninja", "70143836"),
                    new PosterEntry("p_mentalist", "The Mentalist", "Prime Video", "com.amazon.firebat", ""),
                    new PosterEntry("p_sherlock", "Sherlock", "Prime Video", "com.amazon.firebat", ""),
                    new PosterEntry("p_bcs", "Better Call Saul", "Netflix", "com.netflix.ninja", "80021955"),
                    new PosterEntry("p_inception", "Inception", "Prime Video", "com.amazon.firebat", "")
                }),
            new MoodCard("Anything at all", "Surprise me — dealer's choice", 0xFF8B5CF6, 0xFF5B21B6,
                new PosterEntry[]{
                    new PosterEntry("p_inception", "Inception", "Prime Video", "com.amazon.firebat", ""),
                    new PosterEntry("p_office", "The Office", "Prime Video", "com.amazon.firebat", ""),
                    new PosterEntry("p_tdk", "The Dark Knight", "Prime Video", "com.amazon.firebat", ""),
                    new PosterEntry("p_bb", "Breaking Bad", "Netflix", "com.netflix.ninja", "70143836"),
                    new PosterEntry("p_master", "Master", "Prime Video", "com.amazon.firebat", ""),
                    new PosterEntry("p_spidey", "Spider-Man: No Way Home", "SonyLIV", "com.onemainstream.sonyliv.android", ""),
                    new PosterEntry("p_endgame", "Avengers: Endgame", "JioHotstar", "in.startv.hotstar", ""),
                    new PosterEntry("p_cricket1", "Live Cricket Match Center", "JioHotstar", "in.startv.hotstar", ""),
                    new PosterEntry("p_f1_1", "Formula 1: Drive to Survive", "Netflix", "com.netflix.ninja", "80204890")
                })
        };
    }

    private void fetchWeatherAsync() {
        bgExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=28.61&longitude=77.21&current_weather=true");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3500);
                conn.setReadTimeout(3500);
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    String json = sb.toString();
                    int cwIdx = json.indexOf("\"current_weather\"");
                    if (cwIdx >= 0) {
                        int tIdx = json.indexOf("\"temperature\":", cwIdx);
                        if (tIdx >= 0) {
                            int start = tIdx + 14;
                            int end = start;
                            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) {
                                end++;
                            }
                            double temp = Double.parseDouble(json.substring(start, end));
                            final String updated = Math.round(temp) + "°C  Clear · Live";
                            handler.post(() -> {
                                weatherText = updated;
                                invalidate();
                            });
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /**
     * Synchronizes the floating dock with AppAdapter so that:
     * 1. Main apps (TiviMate, Playfy, YouTube, Netflix, JioHotstar) come first in order.
     * 2. Apps toggled ON/OFF by the user in AppLibraryDialog immediately appear/disappear.
     * 3. Doomsday Chess is included right in the dock.
     */
    void syncDockApps(AppAdapter adapter) {
        dockItems.clear();
        Set<String> removed = LibraryApps.loadRemoved(ctx);

        // Map currently visible adapter apps by package
        Map<String, Integer> adapterPosByPkg = new HashMap<>();
        if (adapter != null) {
            for (int i = 0; i < adapter.appCount(); i++) {
                String pkg = adapter.packageAt(i);
                if (pkg != null && !adapterPosByPkg.containsKey(pkg)) {
                    adapterPosByPkg.put(pkg, i);
                }
            }
        }

        // Priority main apps requested by user + Reel apps
        String[][] priority = {
            {"ar.tvplayer.tv", "TiviMate", "tivimate", "#00B4D8"},
            {"com.playfy.tv", "Playfy", "playfy", "#A855F7"},
            {"com.amazon.firetv.youtube", "YouTube", "youtube", "#FF1E1E"},
            {"com.netflix.ninja", "Netflix", "netflix", "#E50914"},
            {"in.startv.hotstar", "JioHotstar", "hotstar", "#2563EB"},
            {"com.amazon.firebat", "Prime Video", "prime", "#00A8E1"}
        };

        Set<String> addedPkgs = new HashSet<>();
        for (String[] p : priority) {
            String pkg = p[0];
            if (removed.contains(pkg)) continue; // Respect user toggling off in App Library
            int pos = adapterPosByPkg.containsKey(pkg) ? adapterPosByPkg.get(pkg) : -1;
            dockItems.add(new DockItem(
                pkg, p[1], logoBitmaps.get(p[2]),
                pos >= 0 && adapter != null ? adapter.artworkAt(pos) : null,
                Color.parseColor(p[3]), pos, false
            ));
            addedPkgs.add(pkg);
        }

        // Add Doomsday Chess tile in dock
        dockItems.add(new DockItem(
            "com.gathin.tvhome.chess", "Doomsday Chess", logoBitmaps.get("chess"),
            null, 0xFF16A34A, -1, true
        ));

        // Add any extra apps the user toggled ON in AppLibraryDialog
        if (adapter != null) {
            for (int i = 0; i < adapter.appCount(); i++) {
                String pkg = adapter.packageAt(i);
                if (pkg == null || addedPkgs.contains(pkg) || pkg.startsWith("com.gathin.tvhome")) continue;
                String label = adapter.labelAt(i);
                if (label == null || label.isEmpty()) continue;
                Bitmap whiteLogo = null;
                if (pkg.contains("apple")) whiteLogo = logoBitmaps.get("appletv");
                dockItems.add(new DockItem(
                    pkg, label, whiteLogo, adapter.artworkAt(i),
                    adapter.ambientColorAt(i), i, false
                ));
                addedPkgs.add(pkg);
            }
        }

        if (dockIndex >= dockItems.size()) {
            dockIndex = Math.max(0, dockItems.size() - 1);
        }
        updateDockGlow(false);
        invalidate();
    }

    private void updateDockGlow(boolean animate) {
        if (dockItems.isEmpty()) return;
        int target = dockItems.get(dockIndex).glowColor;
        if (!animate) {
            currentGlowColor = target;
            invalidate();
            return;
        }
        if (glowAnimator != null) glowAnimator.cancel();
        int start = currentGlowColor;
        glowAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), start, target);
        glowAnimator.setDuration(280L);
        glowAnimator.addUpdateListener(a -> {
            currentGlowColor = (int) a.getAnimatedValue();
            invalidate();
        });
        glowAnimator.start();
    }

    void scrollToSection(int targetSection) {
        targetSection = Math.max(0, Math.min(3, targetSection));
        if (targetSection == currentSection && (sectionAnimator == null || !sectionAnimator.isRunning())) return;
        currentSection = targetSection;
        if (currentSection == 0) {
            taglineIndex = (taglineIndex + 1) % TAGLINES.length;
        }
        int h = getHeight() > 0 ? getHeight() : 1080;
        float targetY = currentSection * h;
        if (sectionAnimator != null) sectionAnimator.cancel();
        sectionAnimator = ValueAnimator.ofFloat(cameraY, targetY);
        sectionAnimator.setDuration(420L);
        sectionAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        sectionAnimator.addUpdateListener(a -> {
            cameraY = (float) a.getAnimatedValue();
            invalidate();
        });
        sectionAnimator.start();
        triggerSectionAnimations();
    }

    private void triggerSectionAnimations() {
        handler.removeCallbacks(animationTick);
        if (currentSection == 1) {
            if (favIndex == 4) reverseFlashFocusStartMs = SystemClock.uptimeMillis();
            if (favIndex == 5) spideyFocusStartMs = SystemClock.uptimeMillis();
        }
        handler.post(animationTick);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cameraY = currentSection * h;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Render visible sections based on cameraY
        for (int sec = 0; sec < 4; sec++) {
            float secTop = sec * h - cameraY;
            if (secTop + h <= 0 || secTop >= h) continue;
            canvas.save();
            canvas.translate(0, secTop);
            canvas.clipRect(0, 0, w, h);
            switch (sec) {
                case 0:
                    drawSection0HeroHome(canvas, w, h);
                    break;
                case 1:
                    drawSection1Favourites(canvas, w, h);
                    break;
                case 2:
                    drawSection2MoodRoulette(canvas, w, h);
                    break;
                case 3:
                    drawSection3DoomsdayChess(canvas, w, h);
                    break;
            }
            canvas.restore();
        }
    }

    // =========================================================================
    // SECTION 0: HERO HOME + FLOATING WHITE-LOGO DOCK
    // =========================================================================
    private void drawSection0HeroHome(Canvas canvas, int w, int h) {
        // 1. Full-screen Hero Wallpaper (user's selected wallpaper from wallpaper/ or Doctor Doom)
        Bitmap wp = customWallpaperOverride != null ? customWallpaperOverride : host.getCurrentWallpaper();
        if (wp == null) wp = defaultDoomWallpaper;
        if (wp != null) {
            drawCenterCrop(canvas, wp, w, h, 255);
        } else {
            canvas.drawColor(0xFF060D09);
        }

        // 2. Top & Bottom atmospheric scrims for legible typography and floating dock
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, 0, 0, h * 0.25f,
                0x88000000, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h * 0.25f, paint);

        paint.setShader(new LinearGradient(0, h * 0.52f, 0, h,
                new int[]{0x00000000, 0xAA030604, 0xF2020403},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, h * 0.52f, w, h, paint);
        paint.setShader(null);

        // 3. Top-Left Clock + Weather
        float padX = w * 0.048f;
        textPaint.setTypeface(outfitRegular);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(42f * density);
        String timeStr = clockFormat.format(new Date());
        canvas.drawText(timeStr, padX, h * 0.095f, textPaint);

        textPaint.setColor(0xB3FFFFFF);
        textPaint.setTextSize(19f * density);
        canvas.drawText(weatherText, padX, h * 0.132f, textPaint);

        // 4. Top-Right 4 Minimalist Bare Icons (Wi-Fi, Toggle Apps, Wallpapers, Settings)
        drawTopRightIcons(canvas, w, h);

        // 5. Witty Tagline above Dock
        textPaint.setTypeface(outfitRegular);
        textPaint.setColor(0xEAFFFFFF);
        textPaint.setTextSize(32f * density);
        canvas.drawText(TAGLINES[taglineIndex], padX, h * 0.725f, textPaint);

        // 6. Floating White-Logo Dock + Brand Radial Glow
        if (!dockItems.isEmpty()) {
            int count = dockItems.size();
            int maxVisible = Math.min(count, 7);
            int startIdx = Math.max(0, Math.min(dockIndex - 3, count - maxVisible));
            float slotW = (w - padX * 2f) / maxVisible;
            float dockCenterY = h * 0.835f;

            // Brand-colored radial glow behind focused dock item
            if (!topBarFocused) {
                int visPos = dockIndex - startIdx;
                float glowX = padX + (visPos + 0.5f) * slotW;
                int r = Color.red(currentGlowColor);
                int g = Color.green(currentGlowColor);
                int b = Color.blue(currentGlowColor);
                glowPaint.setShader(new RadialGradient(
                        glowX, dockCenterY, slotW * 1.35f,
                        new int[]{Color.argb(185, r, g, b), Color.argb(70, r, g, b), 0x00000000},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawCircle(glowX, dockCenterY, slotW * 1.35f, glowPaint);
                glowPaint.setShader(null);
            }

            for (int i = 0; i < maxVisible; i++) {
                int idx = startIdx + i;
                DockItem item = dockItems.get(idx);
                boolean focused = !topBarFocused && idx == dockIndex;
                float cx = padX + (i + 0.5f) * slotW;
                float scale = focused ? 1.20f : 0.94f;
                int alpha = focused ? 255 : 130;

                float logoW = Math.min(slotW * 0.78f, 128f * density) * scale;
                float logoH = logoW * (170f / 260f);

                if (item.whiteLogo != null) {
                    paint.setAlpha(alpha);
                    rect.set(cx - logoW * 0.5f, dockCenterY - logoH * 0.5f,
                            cx + logoW * 0.5f, dockCenterY + logoH * 0.5f);
                    canvas.drawBitmap(item.whiteLogo, null, rect, paint);
                    paint.setAlpha(255);
                } else if (item.fallbackDrawable != null) {
                    int fw = Math.round(logoW * 0.78f);
                    int fh = Math.round(logoH * 0.78f);
                    item.fallbackDrawable.setAlpha(alpha);
                    item.fallbackDrawable.setBounds(
                            Math.round(cx - fw * 0.5f), Math.round(dockCenterY - fh * 0.5f),
                            Math.round(cx + fw * 0.5f), Math.round(dockCenterY + fh * 0.5f));
                    item.fallbackDrawable.draw(canvas);
                    item.fallbackDrawable.setAlpha(255);
                } else {
                    textPaint.setTypeface(outfitBold);
                    textPaint.setTextSize((focused ? 22f : 18f) * density);
                    textPaint.setColor(Color.argb(alpha, 255, 255, 255));
                    float tw = textPaint.measureText(item.label);
                    canvas.drawText(item.label, cx - tw * 0.5f, dockCenterY + 7f * density, textPaint);
                }

                if (focused) {
                    // Crisp white indicator bar + app name under focused logo
                    paint.setColor(0xFFFFFFFF);
                    float barW = 36f * density;
                    rect.set(cx - barW * 0.5f, dockCenterY + logoH * 0.50f + 4f * density,
                            cx + barW * 0.5f, dockCenterY + logoH * 0.50f + 7.5f * density);
                    canvas.drawRoundRect(rect, 4f, 4f, paint);

                    textPaint.setTypeface(outfitBold);
                    textPaint.setTextSize(15f * density);
                    textPaint.setColor(0xEEFFFFFF);
                    float lw = textPaint.measureText(item.label);
                    canvas.drawText(item.label, cx - lw * 0.5f,
                            dockCenterY + logoH * 0.50f + 26f * density, textPaint);
                }
            }
        }

        // 7. Bottom scroll indicator hint
        textPaint.setTypeface(outfitRegular);
        textPaint.setTextSize(14f * density);
        textPaint.setColor(0x88FFFFFF);
        String hint = "↓  Scroll Down: My Favourites  ·  Mood Roulette  ·  Avengers: Doomsday Chess   |   Hold OK / Menu: Toggle Apps";
        float hw = textPaint.measureText(hint);
        canvas.drawText(hint, (w - hw) * 0.5f, h * 0.972f, textPaint);
    }

    private void drawTopRightIcons(Canvas canvas, int w, int h) {
        String[] labels = {"Change Theme", "Wi-Fi", "Toggle Apps", "Wallpapers", "Settings"};
        float rightEdge = w * 0.952f;
        float cy = h * 0.082f;
        float spacing = 54f * density;
        float r = 18f * density;

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        for (int i = 0; i < 5; i++) {
            float cx = rightEdge - (4 - i) * spacing;
            boolean focused = topBarFocused && topBarIndex == i;

            if (focused) {
                paint.setColor(0x33FFFFFF);
                canvas.drawCircle(cx, cy, r * 1.35f, paint);
                strokePaint.setStrokeWidth(2f * density);
                strokePaint.setColor(0xFF4ADE80);
                canvas.drawCircle(cx, cy, r * 1.35f, strokePaint);

                textPaint.setTypeface(outfitBold);
                textPaint.setTextSize(13f * density);
                textPaint.setColor(0xFFFFFFFF);
                float tw = textPaint.measureText(labels[i]);
                canvas.drawText(labels[i], cx - tw * 0.5f, cy + r * 2.15f, textPaint);
            }

            int iconColor = focused ? 0xFFFFFFFF : 0xCCFFFFFF;
            strokePaint.setColor(iconColor);
            strokePaint.setStrokeWidth(2.2f * density);
            float s = 9.5f * density;

            if (i == 0) {
                // Theme Switcher Diamond / Sparkle Palette icon
                path.reset();
                path.moveTo(cx, cy - s * 0.95f);
                path.lineTo(cx + s * 0.95f, cy);
                path.lineTo(cx, cy + s * 0.95f);
                path.lineTo(cx - s * 0.95f, cy);
                path.close();
                canvas.drawPath(path, strokePaint);
                paint.setColor(iconColor);
                canvas.drawCircle(cx, cy, 2.5f * density, paint);
            } else if (i == 1) {
                // Wi-Fi arcs + dot
                rect.set(cx - s, cy - s * 0.7f, cx + s, cy + s * 1.3f);
                canvas.drawArc(rect, 225, 90, false, strokePaint);
                rect.set(cx - s * 0.65f, cy - s * 0.35f, cx + s * 0.65f, cy + s * 0.95f);
                canvas.drawArc(rect, 225, 90, false, strokePaint);
                paint.setColor(iconColor);
                canvas.drawCircle(cx, cy + s * 0.55f, 2.2f * density, paint);
            } else if (i == 2) {
                // 2x2 App Grid icon (Toggle Home Apps)
                float g = s * 0.42f;
                float off = s * 0.52f;
                rect.set(cx - off - g, cy - off - g, cx - off + g, cy - off + g);
                canvas.drawRoundRect(rect, 2f, 2f, strokePaint);
                rect.set(cx + off - g, cy - off - g, cx + off + g, cy - off + g);
                canvas.drawRoundRect(rect, 2f, 2f, strokePaint);
                rect.set(cx - off - g, cy + off - g, cx - off + g, cy + off + g);
                canvas.drawRoundRect(rect, 2f, 2f, strokePaint);
                rect.set(cx + off - g, cy + off - g, cx + off + g, cy + off + g);
                canvas.drawRoundRect(rect, 2f, 2f, strokePaint);
            } else if (i == 3) {
                // Landscape / Wallpaper Mountain frame icon
                rect.set(cx - s, cy - s * 0.78f, cx + s, cy + s * 0.78f);
                canvas.drawRoundRect(rect, 3f * density, 3f * density, strokePaint);
                path.reset();
                path.moveTo(cx - s * 0.75f, cy + s * 0.55f);
                path.lineTo(cx - s * 0.15f, cy - s * 0.15f);
                path.lineTo(cx + s * 0.25f, cy + s * 0.25f);
                path.lineTo(cx + s * 0.55f, cy - s * 0.05f);
                path.lineTo(cx + s * 0.80f, cy + s * 0.55f);
                canvas.drawPath(path, strokePaint);
            } else {
                // Settings Gear icon
                canvas.drawCircle(cx, cy, s * 0.42f, strokePaint);
                for (int a = 0; a < 360; a += 45) {
                    double rad = Math.toRadians(a);
                    float x1 = cx + (float) Math.cos(rad) * s * 0.58f;
                    float y1 = cy + (float) Math.sin(rad) * s * 0.58f;
                    float x2 = cx + (float) Math.cos(rad) * s * 0.92f;
                    float y2 = cy + (float) Math.sin(rad) * s * 0.92f;
                    canvas.drawLine(x1, y1, x2, y2, strokePaint);
                }
            }
        }
    }

    // =========================================================================
    // SECTION 1: "MY FAVOURITES" 7-CHARACTER LINEUP
    // =========================================================================
    private void drawSection1Favourites(Canvas canvas, int w, int h) {
        long now = SystemClock.uptimeMillis();

        // 1. Monochrome full-lineup base layer
        if (favBw != null) {
            drawCenterCrop(canvas, favBw, w, h, 255);
        } else {
            canvas.drawColor(0xFF0A0A0C);
        }

        // 2. Crossfade active character's full-color spotlight image
        float jitterX = 0f;
        float dropY = 0f;
        if (favIndex == 4 && now - reverseFlashFocusStartMs < 1000L) {
            // Reverse-Flash 1s high-frequency speedster phasing vibration
            jitterX = (float) Math.sin(now * 0.19) * (9.5f * density);
        } else if (favIndex == 5 && now - spideyFocusStartMs < 600L) {
            // Spider-Man drop-in thud bounce
            float t = (now - spideyFocusStartMs) / 600f;
            dropY = (float) (-Math.abs(Math.sin(t * Math.PI * 2.2)) * (1f - t) * 28f * density);
        }

        if (favCrossfade < 1f && prevFavIndex >= 0 && favColor[prevFavIndex] != null) {
            drawCenterCrop(canvas, favColor[prevFavIndex], w, h, Math.round((1f - favCrossfade) * 255));
        }
        if (favColor[favIndex] != null) {
            canvas.save();
            canvas.translate(jitterX, dropY);
            drawCenterCrop(canvas, favColor[favIndex], w, h, Math.round(favCrossfade * 255));
            canvas.restore();
        }

        // 3. Top & Bottom cinematic vignette
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, 0, 0, h * 0.28f,
                0xCC000000, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h * 0.28f, paint);
        paint.setShader(new LinearGradient(0, h * 0.72f, 0, h,
                0x00000000, 0xEE000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, h * 0.72f, w, h, paint);
        paint.setShader(null);

        // 4. Section Header
        textPaint.setTypeface(outfitBold);
        textPaint.setTextSize(38f * density);
        textPaint.setColor(0xFFFFFFFF);
        canvas.drawText("My Favourites", w * 0.048f, h * 0.095f, textPaint);

        // 5. Character-Specific Head Position (High-res effects are baked directly into fav_0..6.jpg)
        float headX = w * FAV_HEAD_X[favIndex];

        // 6. Floating Show Callout & Streaming Badge above focused character
        String showTitle = FAV_SHOWS[favIndex];
        String charName = FAV_CHARACTERS[favIndex];
        String platform = FAV_PLATFORMS[favIndex];

        textPaint.setTypeface(outfitBold);
        textPaint.setTextSize(24f * density);
        float titleW = textPaint.measureText(showTitle);
        float cardW = Math.max(titleW + 44f * density, 220f * density);
        float cardH = 86f * density;
        float cardLeft = Math.max(w * 0.04f, Math.min(w * 0.96f - cardW, headX - cardW * 0.5f));
        float cardTop = h * 0.085f;

        rect.set(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH);
        paint.setColor(0xD90F172A);
        canvas.drawRoundRect(rect, 16f * density, 16f * density, paint);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f * density);
        strokePaint.setColor(FAV_BADGE_COLORS[favIndex]);
        canvas.drawRoundRect(rect, 16f * density, 16f * density, strokePaint);

        textPaint.setColor(0xFFFFFFFF);
        canvas.drawText(showTitle, cardLeft + 20f * density, cardTop + 34f * density, textPaint);

        // Platform Pill Badge inside callout
        textPaint.setTextSize(13f * density);
        float badgeW = textPaint.measureText(platform) + 20f * density;
        rect.set(cardLeft + 20f * density, cardTop + 47f * density,
                cardLeft + 20f * density + badgeW, cardTop + 72f * density);
        paint.setColor(FAV_BADGE_COLORS[favIndex]);
        canvas.drawRoundRect(rect, 8f * density, 8f * density, paint);
        textPaint.setColor(0xFFFFFFFF);
        canvas.drawText(platform, cardLeft + 30f * density, cardTop + 64f * density, textPaint);

        textPaint.setTypeface(outfitRegular);
        textPaint.setColor(0xCCFFFFFF);
        textPaint.setTextSize(14f * density);
        canvas.drawText("·  " + charName, cardLeft + 28f * density + badgeW, cardTop + 64f * density, textPaint);

        // 7. Bottom 7-Character Selector Strip (evenly spaced so names never overlap)
        float[] stripX = {0.11f, 0.24f, 0.36f, 0.47f, 0.60f, 0.74f, 0.88f};
        float stripY = h * 0.915f;
        for (int i = 0; i < 7; i++) {
            float cx = w * stripX[i];
            boolean sel = (i == favIndex);
            textPaint.setTypeface(sel ? outfitBold : outfitRegular);
            textPaint.setTextSize((sel ? 16f : 13.5f) * density);
            textPaint.setColor(sel ? 0xFFFFFFFF : 0x77FFFFFF);
            float nw = textPaint.measureText(FAV_CHARACTERS[i]);
            canvas.drawText(FAV_CHARACTERS[i], cx - nw * 0.5f, stripY, textPaint);

            if (sel) {
                paint.setColor(FAV_BADGE_COLORS[i]);
                rect.set(cx - 24f * density, stripY + 8f * density, cx + 24f * density, stripY + 12f * density);
                canvas.drawRoundRect(rect, 3f, 3f, paint);
            }
        }
    }

    // =========================================================================
    // SECTION 2: "DON'T HAVE TIME TO BROWSE?" MOOD CARDS & SLOT-MACHINE REEL
    // =========================================================================
    private void drawSection2MoodRoulette(Canvas canvas, int w, int h) {
        // Dark cinematic background with subtle mood-colored ambient glow
        MoodCard currentMood = moodCards[moodIndex];
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new RadialGradient(
                w * 0.5f, h * 0.48f, Math.max(w, h) * 0.65f,
                new int[]{ Color.argb(95, Color.red(currentMood.colorTop),
                        Color.green(currentMood.colorTop), Color.blue(currentMood.colorTop)),
                        0xFF070A0F, 0xFF030407 },
                new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // Top Header
        textPaint.setTypeface(outfitBold);
        textPaint.setTextSize(42f * density);
        textPaint.setColor(0xFFFFFFFF);
        String header = "Don't have time to browse?";
        float hw = textPaint.measureText(header);
        canvas.drawText(header, (w - hw) * 0.5f, h * 0.135f, textPaint);

        textPaint.setTypeface(outfitRegular);
        textPaint.setTextSize(19f * density);
        textPaint.setColor(0xB3FFFFFF);
        String sub = reelMode
                ? (reelSpinning
                        ? "Spinning the watchlist reel — Fate is picking your tonight's watch..."
                        : "OK = Watch Now   ·   Right = Spin Again   ·   Left = Back to Moods")
                : "Left = Nope (Next Mood)   ·   Right / OK = Yes (Spin the Slot Reel)";
        float sw = textPaint.measureText(sub);
        canvas.drawText(sub, (w - sw) * 0.5f, h * 0.185f, textPaint);

        if (!reelMode) {
            drawMoodCardStack(canvas, w, h);
        } else {
            drawSlotMachineReel(canvas, w, h);
        }

        // Bottom Row Pills: "More apps" (Toggle Home Apps) and "Wallpapers" (Pick from wallpaper/)
        drawSection2BottomPills(canvas, w, h);
    }

    private void drawMoodCardStack(Canvas canvas, int w, int h) {
        float cardW = w * 0.48f;
        float cardH = h * 0.48f;
        float cx = w * 0.5f;
        float cy = h * 0.50f;

        // Draw next mood card peeking underneath
        MoodCard nextMood = moodCards[(moodIndex + 1) % moodCards.length];
        float backScale = 0.92f + 0.08f * cardDismissProgress;
        canvas.save();
        canvas.translate(cx, cy + (1f - cardDismissProgress) * 20f * density);
        canvas.scale(backScale, backScale);
        drawSingleMoodCard(canvas, nextMood, cardW, cardH, false, cardDismissProgress < 0.15f);
        canvas.restore();

        // Draw active mood card (with swipe-left fly-off animation when Nope is pressed)
        MoodCard activeMood = moodCards[moodIndex];
        canvas.save();
        float flyX = -cardDismissProgress * w * 0.55f;
        float rot = -cardDismissProgress * 15f;
        canvas.translate(cx + flyX, cy);
        canvas.rotate(rot);
        drawSingleMoodCard(canvas, activeMood, cardW, cardH, sec2FocusRow == 0, false);
        canvas.restore();

        // Left / Right side callout badges
        textPaint.setTypeface(outfitBold);
        textPaint.setTextSize(18f * density);
        textPaint.setColor(0xCCEF4444);
        canvas.drawText("←  NOPE", w * 0.14f, cy, textPaint);

        textPaint.setColor(0xCC4ADE80);
        String yesText = "YES (SPIN)  →";
        float yw = textPaint.measureText(yesText);
        canvas.drawText(yesText, w * 0.86f - yw, cy, textPaint);
    }

    private void drawSingleMoodCard(Canvas canvas, MoodCard card, float cardW, float cardH, boolean focused, boolean isBackDeckPeek) {
        rect.set(-cardW * 0.5f, -cardH * 0.5f, cardW * 0.5f, cardH * 0.5f);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new LinearGradient(0, -cardH * 0.5f, 0, cardH * 0.5f,
                card.colorTop, card.colorBottom, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, 26f * density, 26f * density, paint);
        paint.setShader(null);

        if (isBackDeckPeek) {
            paint.setColor(0x66000000);
            canvas.drawRoundRect(rect, 26f * density, 26f * density, paint);
            paint.setColor(0xFFFFFFFF);
            return;
        }

        if (focused) {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(3.5f * density);
            strokePaint.setColor(0xFFFFFFFF);
            canvas.drawRoundRect(rect, 26f * density, 26f * density, strokePaint);
        }

        // Mood Title & Subtitle on left side of card (clipped before poster fan)
        textPaint.setTypeface(outfitBold);
        textPaint.setTextSize(30f * density);
        textPaint.setColor(0xFFFFFFFF);
        canvas.drawText(card.title, -cardW * 0.43f, -cardH * 0.18f, textPaint);

        textPaint.setTypeface(outfitRegular);
        textPaint.setTextSize(15f * density);
        textPaint.setColor(0xE6FFFFFF);
        canvas.drawText(card.subtitle, -cardW * 0.43f, -cardH * 0.04f, textPaint);

        textPaint.setTypeface(outfitBold);
        textPaint.setTextSize(13.5f * density);
        textPaint.setColor(0xCCFFFFFF);
        canvas.drawText("Press OK / RIGHT to spin " + card.items.length + " picks →",
                -cardW * 0.43f, cardH * 0.34f, textPaint);

        // 3 Fanned Movie/Show Posters on the right side of the card
        float pw = cardH * 0.33f;
        float ph = pw * 1.48f;
        float fanCenterX = cardW * 0.31f;
        float fanCenterY = cardH * 0.04f;
        float[] angles = {-14f, 0f, 14f};
        float[] offsetsX = {-pw * 0.36f, 0f, pw * 0.36f};

        paint.setColor(0xFFFFFFFF);
        for (int i = 0; i < Math.min(3, card.items.length); i++) {
            Bitmap poster = posterBitmaps.get(card.items[i].key);
            if (poster == null) continue;
            canvas.save();
            canvas.translate(fanCenterX + offsetsX[i], fanCenterY);
            canvas.rotate(angles[i]);
            rect.set(-pw * 0.5f, -ph * 0.5f, pw * 0.5f, ph * 0.5f);
            canvas.drawBitmap(poster, null, rect, paint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(2f * density);
            strokePaint.setColor(0x88FFFFFF);
            canvas.drawRoundRect(rect, 8f * density, 8f * density, strokePaint);
            canvas.restore();
        }
    }

    private void drawSlotMachineReel(Canvas canvas, int w, int h) {
        float posterW = w * 0.145f;
        float posterH = posterW * 1.50f;
        float spacing = posterW * 1.16f;
        float cy = h * 0.49f;
        float cx = w * 0.5f;

        for (int i = 0; i < activeReelList.size(); i++) {
            float itemCenterX = cx + i * spacing - reelScrollPx;
            if (itemCenterX + posterW < 0 || itemCenterX - posterW > w) continue;

            float dist = Math.abs(itemCenterX - cx) / spacing;
            float scale = Math.max(0.82f, 1.15f - 0.22f * Math.min(1.5f, dist));
            int alpha = Math.max(90, Math.round(255f - Math.min(1f, dist * 0.45f) * 150f));

            PosterEntry entry = activeReelList.get(i);
            Bitmap bmp = posterBitmaps.get(entry.key);
            float dw = posterW * scale;
            float dh = posterH * scale;
            rect.set(itemCenterX - dw * 0.5f, cy - dh * 0.5f, itemCenterX + dw * 0.5f, cy + dh * 0.5f);

            if (bmp != null) {
                paint.setAlpha(alpha);
                canvas.drawBitmap(bmp, null, rect, paint);
                paint.setAlpha(255);
            } else {
                paint.setColor(0xFF1E293B);
                canvas.drawRoundRect(rect, 12f * density, 12f * density, paint);
            }
        }

        // Center Golden Selection Frame
        float winW = posterW * 1.16f;
        float winH = posterH * 1.16f;
        rect.set(cx - winW * 0.5f, cy - winH * 0.5f, cx + winW * 0.5f, cy + winH * 0.5f);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(4f * density);
        strokePaint.setColor(0xFFFBBF24);
        canvas.drawRoundRect(rect, 14f * density, 14f * density, strokePaint);

        // Winner Callout & Auto-Open Countdown once landed
        if (!reelSpinning && reelWinnerIdx < activeReelList.size()) {
            PosterEntry winner = activeReelList.get(reelWinnerIdx);
            textPaint.setTypeface(outfitBold);
            textPaint.setTextSize(26f * density);
            textPaint.setColor(0xFFFFFFFF);
            String title = winner.title + "  ·  " + winner.platform;
            float tw = textPaint.measureText(title);
            canvas.drawText(title, (w - tw) * 0.5f, cy + winH * 0.5f + 36f * density, textPaint);

            long elapsed = SystemClock.uptimeMillis() - reelLandedMs;
            float progress = Math.max(0f, Math.min(1f, elapsed / 3000f));
            float barW = 280f * density;
            float barTop = cy + winH * 0.5f + 50f * density;
            rect.set(cx - barW * 0.5f, barTop, cx + barW * 0.5f, barTop + 8f * density);
            paint.setColor(0x44FFFFFF);
            canvas.drawRoundRect(rect, 4f * density, 4f * density, paint);

            rect.set(cx - barW * 0.5f, barTop, cx - barW * 0.5f + barW * (1f - progress), barTop + 8f * density);
            paint.setColor(0xFFFBBF24);
            canvas.drawRoundRect(rect, 4f * density, 4f * density, paint);
        }
    }

    private void drawSection2BottomPills(Canvas canvas, int w, int h) {
        String[] pills = {
            "More apps (Toggle Apps)",
            "Wallpapers (Backgrounds)",
            "Change Theme (Original / Marvel)"
        };
        float pillW = 248f * density;
        float pillH = 46f * density;
        float gap = 20f * density;
        float startX = (w - (pillW * 3f + gap * 2f)) * 0.5f;
        float topY = h * 0.875f;

        for (int i = 0; i < 3; i++) {
            boolean focused = (sec2FocusRow == 1 && sec2PillIndex == i);
            float left = startX + i * (pillW + gap);
            rect.set(left, topY, left + pillW, topY + pillH);
            paint.setColor(focused ? 0xFFFFFFFF : 0x26FFFFFF);
            canvas.drawRoundRect(rect, 23f * density, 23f * density, paint);

            textPaint.setTypeface(outfitBold);
            textPaint.setTextSize(14.5f * density);
            textPaint.setColor(focused ? 0xFF090D16 : 0xEEFFFFFF);
            float tw = textPaint.measureText(pills[i]);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float ty = rect.centerY() - (fm.ascent + fm.descent) * 0.5f;
            canvas.drawText(pills[i], rect.centerX() - tw * 0.5f, ty, textPaint);
        }
        paint.setColor(0xFFFFFFFF);
    }

    // =========================================================================
    // SECTION 3: "AVENGERS: DOOMSDAY CHESS" INTERACTIVE PREVIEW
    // =========================================================================
    private void drawSection3DoomsdayChess(Canvas canvas, int w, int h) {
        long now = SystemClock.uptimeMillis();

        // 1. Deep Latverian Emerald Backdrop
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new RadialGradient(
                w * 0.5f, h * 0.25f, Math.max(w, h) * 0.72f,
                new int[]{0xFF142E1D, 0xFF07120B, 0xFF020403},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // 2. Doctor Doom Looming at the Top
        if (doomBg != null) {
            float breathe = (float) Math.sin(now * 0.002) * (h * 0.005f);
            float doomH = h * 0.42f;
            float doomW = doomH * ((float) doomBg.getWidth() / Math.max(1, doomBg.getHeight()));
            rect.set((w - doomW) * 0.5f, h * 0.01f + breathe, (w + doomW) * 0.5f, h * 0.01f + breathe + doomH);
            canvas.drawBitmap(doomBg, null, rect, paint);
        }

        // 3. Doom Speech Bubble ("Wait, is that Loki?")
        String bubbleText = "Wait, is that Loki?";
        textPaint.setTypeface(outfitBold);
        textPaint.setTextSize(18f * density);
        float bw = textPaint.measureText(bubbleText) + 36f * density;
        float bh = 46f * density;
        float bx = w * 0.58f;
        float by = h * 0.09f;
        rect.set(bx, by, bx + bw, by + bh);
        paint.setColor(0xE60D1B12);
        canvas.drawRoundRect(rect, 14f * density, 14f * density, paint);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f * density);
        strokePaint.setColor(0xFF4ADE80);
        canvas.drawRoundRect(rect, 14f * density, 14f * density, strokePaint);
        textPaint.setColor(0xFFF0FDF4);
        canvas.drawText(bubbleText, bx + 18f * density, by + 29f * density, textPaint);

        // 4. 3D Perspective Chessboard + All 26 Sculpted Marvel Pieces
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                PointF p00 = projectChess(c, r, w, h);
                PointF p10 = projectChess(c + 1, r, w, h);
                PointF p11 = projectChess(c + 1, r + 1, w, h);
                PointF p01 = projectChess(c, r + 1, w, h);
                path.reset();
                path.moveTo(p00.x, p00.y);
                path.lineTo(p10.x, p10.y);
                path.lineTo(p11.x, p11.y);
                path.lineTo(p01.x, p01.y);
                path.close();
                paint.setColor(((r + c) & 1) == 0 ? 0xFFD8E0C9 : 0xFF1B2B1F);
                canvas.drawPath(path, paint);
            }
        }

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                ChessEngine.Piece piece = previewChess.board[r][c];
                if (piece == null) continue;
                Bitmap bmp = chessSprites.get(piece.sprite);
                if (bmp == null) continue;
                PointF p = projectChess(c + 0.5f, r + 0.5f, w, h);
                float scale = 0.62f + 0.38f * (r / 7f);
                float ph = h * 0.125f * scale;
                float pw = ph * ((float) bmp.getWidth() / Math.max(1, bmp.getHeight()));
                rect.set(p.x - pw * 0.5f, p.y - ph * 0.92f, p.x + pw * 0.5f, p.y + ph * 0.08f);
                canvas.drawBitmap(bmp, null, rect, paint);
            }
        }

        // 5. Glowing Play CTA Button at Bottom Center
        String cta = "PLAY AVENGERS: DOOMSDAY CHESS   (Press OK)";
        textPaint.setTypeface(outfitBold);
        textPaint.setTextSize(18f * density);
        float cw = textPaint.measureText(cta) + 56f * density;
        float ch = 52f * density;
        rect.set((w - cw) * 0.5f, h * 0.90f, (w + cw) * 0.5f, h * 0.90f + ch);
        paint.setColor(0xFF16A34A);
        canvas.drawRoundRect(rect, 26f * density, 26f * density, paint);
        strokePaint.setColor(0xFF86EFAC);
        strokePaint.setStrokeWidth(2.5f * density);
        canvas.drawRoundRect(rect, 26f * density, 26f * density, strokePaint);
        textPaint.setColor(0xFFFFFFFF);
        canvas.drawText(cta, (w - cw) * 0.5f + 28f * density, h * 0.90f + 33f * density, textPaint);
    }

    private PointF projectChess(float col, float row, int w, int h) {
        float u = row / 8f;
        float depth = (float) Math.pow(u, 1.16f);
        float topY = h * 0.30f;
        float bottomY = h * 0.85f;
        float y = topY + (bottomY - topY) * depth;
        float topW = w * 0.44f;
        float bottomW = w * 0.72f;
        float rw = topW + (bottomW - topW) * depth;
        float leftX = (w - rw) * 0.5f;
        return new PointF(leftX + (col / 8f) * rw, y);
    }

    private void drawCenterCrop(Canvas canvas, Bitmap bmp, int w, int h, int alpha) {
        int bw = bmp.getWidth();
        int bh = bmp.getHeight();
        if (bw <= 0 || bh <= 0) return;
        float scale = Math.max((float) w / bw, (float) h / bh);
        float dx = (w - bw * scale) * 0.5f;
        float dy = (h - bh * scale) * 0.5f;
        matrix.reset();
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
        paint.setAlpha(alpha);
        canvas.drawBitmap(bmp, matrix, paint);
        paint.setAlpha(255);
    }

    // =========================================================================
    // D-PAD & REMOTE NAVIGATION
    // =========================================================================
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            playSoundEffect(SoundEffectConstants.CLICK);
            host.onOpenAppLibrary();
            return true;
        }
        boolean isOk = keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (isOk) {
            if (event.getRepeatCount() == 0) {
                okDownTimeMs = SystemClock.uptimeMillis();
                okLongHandled = false;
            } else if (!okLongHandled && SystemClock.uptimeMillis() - okDownTimeMs > 550L) {
                okLongHandled = true;
                playSoundEffect(SoundEffectConstants.CLICK);
                host.onOpenAppLibrary();
            }
            return true;
        }

        if (currentSection == 0) {
            return handleSection0Key(keyCode);
        } else if (currentSection == 1) {
            return handleSection1Key(keyCode);
        } else if (currentSection == 2) {
            return handleSection2Key(keyCode);
        } else {
            return handleSection3Key(keyCode);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean isOk = keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (isOk) {
            if (!okLongHandled && !event.isCanceled()) {
                playSoundEffect(SoundEffectConstants.CLICK);
                handleOkPress();
            }
            okLongHandled = false;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean handleSection0Key(int keyCode) {
        if (topBarFocused) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (topBarIndex > 0) {
                        topBarIndex--;
                        playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                        invalidate();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (topBarIndex < 4) {
                        topBarIndex++;
                        playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                        invalidate();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_BACK:
                    topBarFocused = false;
                    playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    return true;
            }
        } else {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (dockIndex > 0) {
                        dockIndex--;
                        playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                        updateDockGlow(true);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (dockIndex < dockItems.size() - 1) {
                        dockIndex++;
                        playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                        updateDockGlow(true);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    topBarFocused = true;
                    playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                    scrollToSection(1);
                    return true;
            }
        }
        return false;
    }

    private boolean handleSection1Key(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (favIndex > 0) {
                    switchFavourite(favIndex - 1);
                    playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (favIndex < 6) {
                    switchFavourite(favIndex + 1);
                    playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_BACK:
                playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                scrollToSection(0);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                scrollToSection(2);
                return true;
        }
        return false;
    }

    private void switchFavourite(int nextIdx) {
        prevFavIndex = favIndex;
        favIndex = nextIdx;
        if (favAnimator != null) favAnimator.cancel();
        favCrossfade = 0f;
        favAnimator = ValueAnimator.ofFloat(0f, 1f);
        favAnimator.setDuration(240L);
        favAnimator.addUpdateListener(a -> {
            favCrossfade = (float) a.getAnimatedValue();
            invalidate();
        });
        favAnimator.start();
        triggerSectionAnimations();
    }

    private boolean handleSection2Key(int keyCode) {
        if (reelMode) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (reelAnimator != null) reelAnimator.cancel();
                reelMode = false;
                reelSpinning = false;
                reelLandedMs = 0L;
                playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                invalidate();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                playSoundEffect(SoundEffectConstants.CLICK);
                startSlotMachineReel();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (reelAnimator != null) reelAnimator.cancel();
                reelMode = false;
                reelSpinning = false;
                reelLandedMs = 0L;
                scrollToSection(3);
                return true;
            }
            return true;
        }

        if (sec2FocusRow == 0) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                    dismissMoodCardAndAdvance();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    playSoundEffect(SoundEffectConstants.CLICK);
                    startSlotMachineReel();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    sec2FocusRow = 1;
                    playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                    scrollToSection(1);
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    scrollToSection(0);
                    return true;
            }
        } else {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (sec2PillIndex > 0) sec2PillIndex--;
                    playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT);
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (sec2PillIndex < 2) sec2PillIndex++;
                    playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT);
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    sec2FocusRow = 0;
                    playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    sec2FocusRow = 0;
                    playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN);
                    scrollToSection(3);
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    scrollToSection(0);
                    return true;
            }
        }
        return false;
    }

    private boolean handleSection3Key(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                scrollToSection(2);
                return true;
            case KeyEvent.KEYCODE_BACK:
                playSoundEffect(SoundEffectConstants.NAVIGATION_UP);
                scrollToSection(0);
                return true;
        }
        return false;
    }

    private void handleOkPress() {
        if (currentSection == 0) {
            if (topBarFocused) {
                if (topBarIndex == 0) host.onSwitchThemeRequested();
                else if (topBarIndex == 1) host.onOpenWifiSettings();
                else if (topBarIndex == 2) host.onOpenAppLibrary();
                else if (topBarIndex == 3) showInstantWallpaperPicker();
                else host.onOpenSystemSettings();
            } else if (!dockItems.isEmpty() && dockIndex < dockItems.size()) {
                DockItem item = dockItems.get(dockIndex);
                if (item.isChess) {
                    ctx.startActivity(new Intent(ctx, DoomsdayChessActivity.class));
                } else {
                    launchPackage(item.pkg, item.label);
                }
            }
        } else if (currentSection == 1) {
            launchContent(FAV_PACKAGES[favIndex], FAV_SHOWS[favIndex], FAV_DEEP_LINKS[favIndex]);
        } else if (currentSection == 2) {
            if (reelMode) {
                if (!reelSpinning && reelWinnerIdx < activeReelList.size()) {
                    reelLandedMs = 0L;
                    PosterEntry winner = activeReelList.get(reelWinnerIdx);
                    launchContent(winner.pkg, winner.title, winner.deepLink);
                }
            } else if (sec2FocusRow == 0) {
                startSlotMachineReel();
            } else {
                if (sec2PillIndex == 0) host.onOpenAppLibrary();
                else if (sec2PillIndex == 1) showInstantWallpaperPicker();
                else host.onSwitchThemeRequested();
            }
        } else if (currentSection == 3) {
            ctx.startActivity(new Intent(ctx, DoomsdayChessActivity.class));
        }
    }

    private void dismissMoodCardAndAdvance() {
        if (cardDismissing) return;
        cardDismissing = true;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(250L);
        va.setInterpolator(new AccelerateInterpolator());
        va.addUpdateListener(a -> {
            cardDismissProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                cardDismissing = false;
                cardDismissProgress = 0f;
                moodIndex = (moodIndex + 1) % moodCards.length;
                invalidate();
            }
        });
        va.start();
    }

    private void startSlotMachineReel() {
        MoodCard mood = moodCards[moodIndex];
        activeReelList.clear();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int len = mood.items.length;
        for (int i = 0; i < 28; i++) {
            PosterEntry pick = mood.items[rng.nextInt(len)];
            if (i > 0 && len > 1 && pick.key.equals(activeReelList.get(i - 1).key)) {
                pick = mood.items[(rng.nextInt(len - 1) + 1) % len];
            }
            activeReelList.add(pick);
        }
        reelWinnerIdx = 18 + rng.nextInt(7); // random landing slot 18..24
        if (len > 1 && activeReelList.get(reelWinnerIdx).key.equals(lastWinnerKey)) {
            for (PosterEntry candidate : mood.items) {
                if (!candidate.key.equals(lastWinnerKey)) {
                    activeReelList.set(reelWinnerIdx, candidate);
                    break;
                }
            }
        }
        lastWinnerKey = activeReelList.get(reelWinnerIdx).key;
        reelMode = true;
        reelSpinning = true;
        reelLandedMs = 0L;

        int w = getWidth() > 0 ? getWidth() : 1920;
        float posterW = w * 0.145f;
        float spacing = posterW * 1.16f;
        float targetScroll = reelWinnerIdx * spacing;

        if (reelAnimator != null) reelAnimator.cancel();
        reelAnimator = ValueAnimator.ofFloat(0f, targetScroll);
        reelAnimator.setDuration(2450L);
        reelAnimator.setInterpolator(new DecelerateInterpolator(2.4f));
        reelAnimator.addUpdateListener(a -> {
            reelScrollPx = (float) a.getAnimatedValue();
            invalidate();
        });
        reelAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                reelSpinning = false;
                reelLandedMs = SystemClock.uptimeMillis();
                triggerSectionAnimations();
                invalidate();
            }
        });
        reelAnimator.start();
    }

    void applyThemeWallpaper(String fileName) {
        if (fileName == null || fileName.isEmpty() || "00_doctor_doom.jpg".equals(fileName)) {
            customWallpaperOverride = defaultDoomWallpaper;
            host.onWallpaperChosen("00_doctor_doom.jpg");
            invalidate();
            return;
        }
        File dir = Config.dir(ctx);
        if (dir != null) {
            File target = new File(new File(dir, "wallpapers"), fileName);
            if (target.exists()) {
                Bitmap decoded = BitmapFactory.decodeFile(target.getAbsolutePath());
                if (decoded != null) {
                    customWallpaperOverride = decoded;
                    host.onWallpaperChosen(fileName);
                    invalidate();
                    return;
                }
            }
        }
        customWallpaperOverride = defaultDoomWallpaper;
        invalidate();
    }

    /**
     * Opens a dialog listing the Doctor Doom hero wallpaper plus all images inside the
     * wallpaper/ folder pushed to the TV, allowing one-click background switching.
     */
    void showInstantWallpaperPicker() {
        File dir = Config.dir(ctx);
        List<File> files = new ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        List<String> labels = new ArrayList<>();
        labels.add("Doctor Doom: Avengers Doomsday 4K (Default)");
        for (File f : files) {
            labels.add(f.getName());
        }
        labels.add("Switch Theme (Original / Marvel / Cricket / F1)...");
        labels.add("More Launcher Settings...");

        new AlertDialog.Builder(ctx)
                .setTitle("Choose Background Wallpaper")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        customWallpaperOverride = defaultDoomWallpaper;
                        host.onWallpaperChosen("00_doctor_doom.jpg");
                        invalidate();
                    } else if (which <= files.size()) {
                        File chosen = files.get(which - 1);
                        Bitmap decoded = BitmapFactory.decodeFile(chosen.getAbsolutePath());
                        if (decoded != null) {
                            customWallpaperOverride = decoded;
                        }
                        host.onWallpaperChosen(chosen.getName());
                        invalidate();
                    } else if (which == files.size() + 1) {
                        host.onSwitchThemeRequested();
                    } else {
                        host.onOpenLauncherSettings();
                    }
                })
                .show();
    }

    private void launchContent(String pkg, String title, String deepLink) {
        if (pkg == null) return;
        Toast.makeText(ctx, "Opening " + title + "...", Toast.LENGTH_SHORT).show();
        try {
            if ("com.netflix.ninja".equals(pkg) && deepLink != null && !deepLink.isEmpty()) {
                Intent nf = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.netflix.com/watch/" + deepLink));
                nf.putExtra("source", "30");
                nf.setPackage(pkg);
                nf.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                ctx.startActivity(nf);
                return;
            }
            if ("com.amazon.firetv.youtube".equals(pkg) && deepLink != null && !deepLink.isEmpty()) {
                Intent yt = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=" + deepLink));
                yt.setPackage(pkg);
                yt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(yt);
                return;
            }
            // Try direct title search inside JioHotstar, SonyLIV, Prime Video, or Netflix
            Intent searchIntent = new Intent(Intent.ACTION_SEARCH);
            searchIntent.setPackage(pkg);
            searchIntent.putExtra(SearchManager.QUERY, title);
            searchIntent.putExtra("query", title);
            searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (ctx.getPackageManager().resolveActivity(searchIntent, 0) != null) {
                ctx.startActivity(searchIntent);
                return;
            }
        } catch (Exception ignored) {
        }
        launchPackage(pkg, title);
    }

    private void launchPackage(String pkg, String label) {
        if (pkg == null) return;
        Intent intent = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) {
            intent = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(ctx, "Opening " + label + " (" + pkg + ")", Toast.LENGTH_SHORT).show();
    }
}
