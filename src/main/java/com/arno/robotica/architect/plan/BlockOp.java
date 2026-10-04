package com.arno.robotica.architect.plan;

/** One placement of a module, in plot-local coordinates (x and z 0-8, y 0-5). */
public record BlockOp(int x, int y, int z, Piece piece) {
}
