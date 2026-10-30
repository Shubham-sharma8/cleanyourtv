package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Full chess rules, Marvel Doomsday piece roster, opening Magneto defection + Loki entrance,
 * minimax AI opponent (Doctor Doom), and context-reactive Doom dialogue.
 */
public final class ChessEngine {

    public static final int WHITE = 1;
    public static final int BLACK = -1;

    public static final int PAWN = 1;
    public static final int KNIGHT = 2;
    public static final int BISHOP = 3;
    public static final int ROOK = 4;
    public static final int QUEEN = 5;
    public static final int KING = 6;

    public static final class Piece {
        public final int color;
        public int type;
        public int role;
        public String sprite;
        public String heroName;
        public boolean moved;

        public Piece(int color, int type, String sprite, String heroName) {
            this.color = color;
            this.type = type;
            this.role = type;
            this.sprite = sprite;
            this.heroName = heroName;
        }

        public Piece copy() {
            Piece p = new Piece(color, type, sprite, heroName);
            p.moved = moved;
            return p;
        }
    }

    public static final class Move {
        public final int fromR, fromC, toR, toC;
        public final Piece captured;
        public final boolean castleKing, castleQueen, promotion;

        public Move(int fromR, int fromC, int toR, int toC, Piece captured,
                    boolean castleKing, boolean castleQueen, boolean promotion) {
            this.fromR = fromR;
            this.fromC = fromC;
            this.toR = toR;
            this.toC = toC;
            this.captured = captured;
            this.castleKing = castleKing;
            this.castleQueen = castleQueen;
            this.promotion = promotion;
        }
    }

    public final Piece[][] board = new Piece[8][8];
    public int turn = WHITE;
    public Move lastMove;
    public int lastFromR = -1, lastFromC = -1, lastToR = -1, lastToC = -1;
    public boolean gameOver;
    public String statusText = "";
    public String winnerText = "";
    public String doomQuote = "\"Kneel before Doom. Your universe ends in 64 squares.\"";
    private final Random rng = new Random();

    public ChessEngine() {
        resetPreDefection();
    }

    public void reset() {
        resetPreDefection();
    }

    /**
     * Sets up the board right before the opening Magneto defection:
     * Magneto starts at f1 (row 7, col 5) in white, f8 (row 0, col 5) is empty.
     */
    public void resetPreDefection() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                board[r][c] = null;
            }
        }
        turn = WHITE;
        lastMove = null;
        lastFromR = -1;
        lastFromC = -1;
        lastToR = -1;
        lastToC = -1;
        gameOver = false;
        statusText = "";
        winnerText = "";
        doomQuote = "\"Kneel before Doom. Your universe ends in 64 squares.\"";

        // Doom's Graphite Team (Row 0 = Rank 8, Row 1 = Rank 7)
        board[0][0] = new Piece(BLACK, ROOK, "hulk_rook", "Worldbreaker Hulk");
        board[0][1] = new Piece(BLACK, KNIGHT, "spidey_knight", "Symbiote Spider-Man");
        board[0][2] = new Piece(BLACK, BISHOP, "strange_bishop", "Sinister Strange");
        board[0][3] = new Piece(BLACK, QUEEN, "wanda_queen", "Scarlet Witch");
        board[0][4] = new Piece(BLACK, KING, "doom_king", "Doctor Doom");
        board[0][5] = null; // Magneto defects here from f1!
        board[0][6] = new Piece(BLACK, KNIGHT, "namor_knight", "Namor");
        board[0][7] = new Piece(BLACK, ROOK, "sentinel_rook", "Sentinel");

        for (int c = 0; c < 8; c++) {
            board[1][c] = new Piece(BLACK, PAWN, "doombot_pawn", "Doombot");
        }

        // Player's Ivory Team (Row 6 = Rank 2, Row 7 = Rank 1)
        board[6][0] = new Piece(WHITE, PAWN, "thing_pawn", "The Thing");
        board[6][1] = new Piece(WHITE, PAWN, "gambit_pawn", "Gambit");
        board[6][2] = new Piece(WHITE, PAWN, "nightcrawler_pawn", "Nightcrawler");
        board[6][3] = new Piece(WHITE, PAWN, "beast_pawn", "Beast");
        board[6][4] = new Piece(WHITE, PAWN, "panther_pawn", "Black Panther");
        board[6][5] = new Piece(WHITE, PAWN, "shangchi_pawn", "Shang-Chi");
        board[6][6] = new Piece(WHITE, PAWN, "bucky_pawn", "Winter Soldier");
        board[6][7] = new Piece(WHITE, PAWN, "antman_pawn", "Ant-Man");

        board[7][0] = new Piece(WHITE, ROOK, "cyclops_rook", "Cyclops");
        board[7][1] = new Piece(WHITE, KNIGHT, "torch_knight", "Human Torch");
        board[7][2] = new Piece(WHITE, BISHOP, "profx_bishop", "Professor X");
        board[7][3] = new Piece(WHITE, QUEEN, "sue_queen", "Invisible Woman");
        board[7][4] = new Piece(WHITE, KING, "reed_king", "Mister Fantastic");
        board[7][5] = new Piece(WHITE, BISHOP, "magneto_white", "Magneto");
        board[7][6] = new Piece(WHITE, KNIGHT, "thor_knight", "Thor");
        board[7][7] = new Piece(WHITE, ROOK, "steve_rook", "Captain America");
    }

    /** Completes the opening cinematic: Magneto lands on f8 for Doom, Loki steps onto f1 for the Player. */
    public void completeOpeningDefection() {
        board[0][5] = new Piece(BLACK, BISHOP, "magneto_black", "Magneto");
        board[7][5] = new Piece(WHITE, BISHOP, "loki_bishop", "Loki");
    }

    public List<Move> legalMovesFor(int r, int c) {
        List<Move> all = legalMoves(turn);
        List<Move> out = new ArrayList<>();
        for (Move m : all) {
            if (m.fromR == r && m.fromC == c) out.add(m);
        }
        return out;
    }

    public List<Move> legalMoves(int side) {
        List<Move> pseudo = pseudoMoves(board, side);
        List<Move> legal = new ArrayList<>(pseudo.size());
        for (Move m : pseudo) {
            Piece[][] next = cloneBoard(board);
            applyMoveOnBoard(next, m);
            if (!inCheck(next, side)) {
                legal.add(m);
            }
        }
        return legal;
    }

    public String makeMove(Move m) {
        Piece mover = board[m.fromR][m.fromC];
        applyMoveOnBoard(board, m);
        lastMove = m;
        lastFromR = m.fromR;
        lastFromC = m.fromC;
        lastToR = m.toR;
        lastToC = m.toC;
        turn = -turn;

        List<Move> replies = legalMoves(turn);
        boolean check = inCheck(board, turn);
        if (replies.isEmpty()) {
            gameOver = true;
            if (check) {
                statusText = (turn == BLACK) ? "CHECKMATE — YOU DEFEATED DOOM!" : "CHECKMATE — DOOM PREVAILS!";
                winnerText = statusText;
                doomQuote = (turn == BLACK)
                        ? "Impossible... Richards! This timeline is merely a setback!"
                        : "Checkmate. Kneel before Doom.";
                return doomQuote;
            } else {
                statusText = "STALEMATE — DRAW";
                winnerText = statusText;
                doomQuote = "A stalemate? Even eternity bends to Doom's patience.";
                return doomQuote;
            }
        }
        if (check) {
            statusText = (turn == BLACK) ? "DOOM IS IN CHECK" : "YOUR KING IS IN CHECK!";
            doomQuote = (turn == BLACK)
                    ? "You dare threaten Doom directly?! Insolent fool!"
                    : "Richards is cornered. Surrender your timeline.";
            return doomQuote;
        }
        statusText = "";
        if (mover != null && mover.color == WHITE) {
            if (m.captured != null) {
                doomQuote = quoteForPlayerCapture(m.captured);
            } else {
                doomQuote = quoteForPlayerMove(mover);
            }
        } else {
            if (m.captured != null) {
                doomQuote = quoteForDoomCapture(m.captured);
            } else {
                doomQuote = quoteForDoomMove(mover);
            }
        }
        return doomQuote;
    }

    public Move pickAiMove() {
        return computeBestAiMove();
    }

    public Move computeBestAiMove() {
        List<Move> moves = legalMoves(BLACK);
        if (moves.isEmpty()) return null;
        Move best = moves.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (Move m : moves) {
            Piece[][] next = cloneBoard(board);
            applyMoveOnBoard(next, m);
            int score = -negamax(next, 2, -100000, 100000, WHITE);
            score += rng.nextInt(15); // slight variety on equal moves
            if (score > bestScore) {
                bestScore = score;
                best = m;
            }
        }
        return best;
    }

    private int negamax(Piece[][] state, int depth, int alpha, int beta, int side) {
        if (depth == 0) {
            return side * evaluate(state);
        }
        List<Move> pseudo = pseudoMoves(state, side);
        List<Move> legal = new ArrayList<>();
        for (Move m : pseudo) {
            Piece[][] next = cloneBoard(state);
            applyMoveOnBoard(next, m);
            if (!inCheck(next, side)) legal.add(m);
        }
        if (legal.isEmpty()) {
            if (inCheck(state, side)) return -50000 - depth * 100;
            return 0;
        }
        int best = -100000;
        for (Move m : legal) {
            Piece[][] next = cloneBoard(state);
            applyMoveOnBoard(next, m);
            int val = -negamax(next, depth - 1, -beta, -alpha, -side);
            if (val > best) best = val;
            if (val > alpha) alpha = val;
            if (alpha >= beta) break;
        }
        return best;
    }

    private static int evaluate(Piece[][] state) {
        int score = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = state[r][c];
                if (p == null) continue;
                int val = pieceValue(p.type);
                // Center control bonus
                int centerBonus = (3 - Math.abs(3 - r)) * 8 + (3 - Math.abs(3 - c)) * 8;
                if (p.type == PAWN) {
                    int advance = (p.color == WHITE) ? (6 - r) * 12 : (r - 1) * 12;
                    val += advance;
                }
                score += p.color * (val + centerBonus);
            }
        }
        return score;
    }

    private static int pieceValue(int type) {
        switch (type) {
            case PAWN: return 100;
            case KNIGHT: return 320;
            case BISHOP: return 330;
            case ROOK: return 500;
            case QUEEN: return 900;
            case KING: return 20000;
            default: return 0;
        }
    }

    private static Piece[][] cloneBoard(Piece[][] src) {
        Piece[][] dst = new Piece[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (src[r][c] != null) dst[r][c] = src[r][c].copy();
            }
        }
        return dst;
    }

    private static void applyMoveOnBoard(Piece[][] b, Move m) {
        Piece mover = b[m.fromR][m.fromC];
        if (mover == null) return;
        b[m.toR][m.toC] = mover;
        b[m.fromR][m.fromC] = null;
        mover.moved = true;
        if (m.promotion) {
            mover.type = QUEEN;
            mover.role = QUEEN;
            mover.sprite = (mover.color == WHITE) ? "sue_queen" : "wanda_queen";
        }
        if (m.castleKing) {
            Piece rook = b[m.fromR][7];
            b[m.fromR][5] = rook;
            b[m.fromR][7] = null;
            if (rook != null) rook.moved = true;
        } else if (m.castleQueen) {
            Piece rook = b[m.fromR][0];
            b[m.fromR][3] = rook;
            b[m.fromR][0] = null;
            if (rook != null) rook.moved = true;
        }
    }

    public static boolean inCheck(Piece[][] b, int side) {
        int kr = -1, kc = -1;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = b[r][c];
                if (p != null && p.color == side && p.type == KING) {
                    kr = r;
                    kc = c;
                    break;
                }
            }
        }
        if (kr < 0) return true;
        return isSquareAttacked(b, kr, kc, -side);
    }

    private static boolean isSquareAttacked(Piece[][] b, int tr, int tc, int bySide) {
        List<Move> att = pseudoMoves(b, bySide, false);
        for (Move m : att) {
            if (m.toR == tr && m.toC == tc) return true;
        }
        return false;
    }

    private static List<Move> pseudoMoves(Piece[][] b, int side) {
        return pseudoMoves(b, side, true);
    }

    private static List<Move> pseudoMoves(Piece[][] b, int side, boolean includeCastle) {
        List<Move> list = new ArrayList<>();
        int pawnDir = (side == WHITE) ? -1 : 1;
        int startRow = (side == WHITE) ? 6 : 1;
        int promoRow = (side == WHITE) ? 0 : 7;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = b[r][c];
                if (p == null || p.color != side) continue;
                switch (p.type) {
                    case PAWN: {
                        int nr = r + pawnDir;
                        if (nr >= 0 && nr < 8) {
                            if (b[nr][c] == null) {
                                boolean promo = (nr == promoRow);
                                list.add(new Move(r, c, nr, c, null, false, false, promo));
                                int nnr = r + 2 * pawnDir;
                                if (r == startRow && nnr >= 0 && nnr < 8 && b[nnr][c] == null) {
                                    list.add(new Move(r, c, nnr, c, null, false, false, false));
                                }
                            }
                            for (int dc = -1; dc <= 1; dc += 2) {
                                int nc = c + dc;
                                if (nc >= 0 && nc < 8 && b[nr][nc] != null && b[nr][nc].color == -side) {
                                    boolean promo = (nr == promoRow);
                                    list.add(new Move(r, c, nr, nc, b[nr][nc], false, false, promo));
                                }
                            }
                        }
                        break;
                    }
                    case KNIGHT: {
                        int[][] jumps = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
                        for (int[] j : jumps) {
                            int nr = r + j[0], nc = c + j[1];
                            if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                                Piece dst = b[nr][nc];
                                if (dst == null || dst.color == -side) {
                                    list.add(new Move(r, c, nr, nc, dst, false, false, false));
                                }
                            }
                        }
                        break;
                    }
                    case BISHOP:
                    case ROOK:
                    case QUEEN: {
                        int[][] dirs;
                        if (p.type == BISHOP) {
                            dirs = new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
                        } else if (p.type == ROOK) {
                            dirs = new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                        } else {
                            dirs = new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                        }
                        for (int[] d : dirs) {
                            int nr = r + d[0], nc = c + d[1];
                            while (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                                Piece dst = b[nr][nc];
                                if (dst == null) {
                                    list.add(new Move(r, c, nr, nc, null, false, false, false));
                                } else {
                                    if (dst.color == -side) {
                                        list.add(new Move(r, c, nr, nc, dst, false, false, false));
                                    }
                                    break;
                                }
                                nr += d[0];
                                nc += d[1];
                            }
                        }
                        break;
                    }
                    case KING: {
                        for (int dr = -1; dr <= 1; dr++) {
                            for (int dc = -1; dc <= 1; dc++) {
                                if (dr == 0 && dc == 0) continue;
                                int nr = r + dr, nc = c + dc;
                                if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                                    Piece dst = b[nr][nc];
                                    if (dst == null || dst.color == -side) {
                                        list.add(new Move(r, c, nr, nc, dst, false, false, false));
                                    }
                                }
                            }
                        }
                        if (includeCastle && !p.moved && c == 4 && (r == 7 || r == 0)) {
                            Piece kr = b[r][7];
                            if (kr != null && kr.type == ROOK && !kr.moved && b[r][5] == null && b[r][6] == null) {
                                if (!isSquareAttacked(b, r, 4, -side) && !isSquareAttacked(b, r, 5, -side)) {
                                    list.add(new Move(r, c, r, 6, null, true, false, false));
                                }
                            }
                            Piece qr = b[r][0];
                            if (qr != null && qr.type == ROOK && !qr.moved && b[r][1] == null && b[r][2] == null && b[r][3] == null) {
                                if (!isSquareAttacked(b, r, 4, -side) && !isSquareAttacked(b, r, 3, -side)) {
                                    list.add(new Move(r, c, r, 2, null, false, true, false));
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }
        return list;
    }

    private String quoteForPlayerCapture(Piece captured) {
        if ("Doombot".equals(captured.heroName)) {
            String[] q = {
                "A mere Doombot. Did you truly believe that mattered?",
                "Destroy ten Doombots, a hundred more await in Latveria.",
                "Scrap metal. Your real test approaches."
            };
            return q[rng.nextInt(q.length)];
        }
        return "You struck down " + captured.heroName + "? A calculated sacrifice in Doom's grand design.";
    }

    private String quoteForPlayerMove(Piece mover) {
        if ("Loki".equals(mover.heroName)) {
            return "Still trusting the God of Lies? Predictable.";
        }
        if ("Mister Fantastic".equals(mover.heroName)) {
            return "Stretch your mind all you want, Richards. Doom is five moves ahead.";
        }
        if ("Invisible Woman".equals(mover.heroName)) {
            return "Even unseen, no move escapes Doom's gaze.";
        }
        String[] q = {
            "Is that your grand strategy? Pathetic.",
            "Every square you step on belongs to Latveria.",
            "You play for a board. Doom plays for the multiverse.",
            "A bold move... and a fatal miscalculation."
        };
        return q[rng.nextInt(q.length)];
    }

    private String quoteForDoomCapture(Piece captured) {
        return captured.heroName + " falls before Doom! Who will save your timeline now?";
    }

    private String quoteForDoomMove(Piece mover) {
        if ("Magneto".equals(mover.heroName)) {
            return "Erik knows which side survives the incursion.";
        }
        if ("Scarlet Witch".equals(mover.heroName)) {
            return "Chaos magic answers to Doom's order.";
        }
        String[] q = {
            "Your defenses crumble, piece by piece.",
            "Observe how a true sovereign commands the board.",
            "There is no escape from Doomsday."
        };
        return q[rng.nextInt(q.length)];
    }
}
