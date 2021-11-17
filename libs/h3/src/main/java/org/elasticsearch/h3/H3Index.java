/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */
package org.elasticsearch.h3;

/**
 * Functions that can be applied to an H3 index.
 */
class H3Index {

    /**
     * Gets the integer base cell of h3.
     */
    public static int H3_get_base_cell(long h3) {
        return ((int) ((((h3) & H3_BC_MASK) >> H3_BC_OFFSET)));
    }

    /**
     * Returns <code>true</code> if this index is one of twelve pentagons per resolution.
     */
    public static boolean H3_is_pentagon(long h3) {
        return BaseCells.isBaseCellPentagon(H3Index.H3_get_base_cell(h3)) && H3Index.h3LeadingNonZeroDigit(h3) == 0;
    }

    public static long H3_INIT = 35184372088831L;

    /**
     * The bit offset of the mode in an H3 index.
     */
    public static int H3_MODE_OFFSET = 59;

    /**
     * 1's in the 4 mode bits, 0's everywhere else.
     */
    public static long H3_MODE_MASK = 15L << H3_MODE_OFFSET;

    /**
     * 0's in the 4 mode bits, 1's everywhere else.
     */
    public static long H3_MODE_MASK_NEGATIVE = ~H3_MODE_MASK;

    public static long H3_set_mode(long h3, long mode) {
        return (h3 & H3_MODE_MASK_NEGATIVE) | (mode << H3_MODE_OFFSET);
    }

    /**
     * The bit offset of the base cell in an H3 index.
     */
    public static int H3_BC_OFFSET = 45;
    /**
     * 1's in the 7 base cell bits, 0's everywhere else.
     */
    public static long H3_BC_MASK = 127L << H3_BC_OFFSET;

    /**
     * 0's in the 7 base cell bits, 1's everywhere else.
     */
    public static long H3_BC_MASK_NEGATIVE = ~H3_BC_MASK;

    /**
     * Sets the integer base cell of h3 to bc.
     */
    public static long H3_set_base_cell(long h3, long bc) {
        return (h3 & H3_BC_MASK_NEGATIVE) | (bc << H3_BC_OFFSET);
    }

    public static int H3_RES_OFFSET = 52;
    /**
     * 1's in the 4 resolution bits, 0's everywhere else.
     */
    public static long H3_RES_MASK = 15L << H3_RES_OFFSET;

    /**
     * 0's in the 4 resolution bits, 1's everywhere else.
     */
    public static long H3_RES_MASK_NEGATIVE = ~H3_RES_MASK;

    /**
     * The bit offset of the max resolution digit in an H3 index.
     */
    public static int H3_MAX_OFFSET = 63;

    /**
     * 1 in the highest bit, 0's everywhere else.
     */
    public static long H3_HIGH_BIT_MASK = (1L << H3_MAX_OFFSET);

    /**
     * Gets the highest bit of the H3 index.
     */
    public static int H3_get_high_bit(long h3) {
        return ((int) ((((h3) & H3_HIGH_BIT_MASK) >> H3_MAX_OFFSET)));
    }

    /**
     * Sets the long resolution of h3.
     */
    public static long H3_set_resolution(long h3, long res) {
        return (((h3) & H3_RES_MASK_NEGATIVE) | (((res)) << H3_RES_OFFSET));
    }

    /**
     * The bit offset of the reserved bits in an H3 index.
     */
    public static int H3_RESERVED_OFFSET = 56;

    /**
     * 1's in the 3 reserved bits, 0's everywhere else.
     */
    public static long H3_RESERVED_MASK = (7L << H3_RESERVED_OFFSET);

    /**
     * Gets a value in the reserved space. Should always be zero for valid indexes.
     */
    public static int H3_get_reserved_bits(long h3) {
        return ((int) ((((h3) & H3_RESERVED_MASK) >> H3_RESERVED_OFFSET)));
    }

    public static int H3_get_mode(long h3) {
        return ((int) ((((h3) & H3_MODE_MASK) >> H3_MODE_OFFSET)));
    }

    /**
     * Gets the integer resolution of h3.
     */
    public static int H3_get_resolution(long h3) {
        return (int) ((h3 & H3_RES_MASK) >> H3_RES_OFFSET);
    }

    /**
     * The number of bits in a single H3 resolution digit.
     */
    public static int H3_PER_DIGIT_OFFSET = 3;

    /**
     * 1's in the 3 bits of res 15 digit bits, 0's everywhere else.
     */
    public static long H3_DIGIT_MASK = 7L;

    /**
     * Gets the resolution res integer digit (0-7) of h3.
     */
    public static int H3_get_index_digit(long h3, int res) {
        return ((int) ((((h3) >> ((Constants.MAX_H3_RES - (res)) * H3_PER_DIGIT_OFFSET)) & H3_DIGIT_MASK)));
    }

    /**
     * Sets the resolution res digit of h3 to the integer digit (0-7)
     */
    public static long H3_set_index_digit(long h3, int res, long digit) {
        int x = (Constants.MAX_H3_RES - res) * H3_PER_DIGIT_OFFSET;
        return (((h3) & ~((H3_DIGIT_MASK << (x)))) | (((digit)) << x));
    }

    /**
     * Returns whether or not a resolution is a Class III grid. Note that odd
     * resolutions are Class III and even resolutions are Class II.
     * @param res The H3 resolution.
     * @return 1 if the resolution is a Class III grid, and 0 if the resolution is
     *         a Class II grid.
     */
    public static boolean isResolutionClassIII(int res) {
        return res % 2 != 0;
    }

    /**
     * Convert an H3Index to a FaceIJK address.
     * @param h3 The H3Index.
     */
    public static FaceIJK h3ToFaceIjk(long h3) {
        int baseCell = H3Index.H3_get_base_cell(h3);
        if (baseCell < 0 || baseCell >= Constants.NUM_BASE_CELLS) {  // LCOV_EXCL_BR_LINE
            // Base cells less than zero can not be represented in an index
            // To prevent reading uninitialized memory, we zero the output.
            throw new IllegalArgumentException();
        }
        // adjust for the pentagonal missing sequence; all of sub-sequence 5 needs
        // to be adjusted (and some of sub-sequence 4 below)
        if (BaseCells.isBaseCellPentagon(baseCell) && h3LeadingNonZeroDigit(h3) == 5) {
            h3 = h3Rotate60cw(h3);
        }

        // start with the "home" face and ijk+ coordinates for the base cell of c
        FaceIJK fijk = BaseCells.getBaseFaceIJK(baseCell);
        if (h3ToFaceIjkWithInitializedFijk(h3, fijk) == false) {
            return fijk;  // no overage is possible; h lies on this face
        }
        // if we're here we have the potential for an "overage"; i.e., it is
        // possible that c lies on an adjacent face

        CoordIJK origIJK = new CoordIJK(fijk.coord.i, fijk.coord.j, fijk.coord.k);

        // if we're in Class III, drop into the next finer Class II grid
        int res = H3Index.H3_get_resolution(h3);
        if (isResolutionClassIII(res)) {
            // Class III
            fijk.coord.downAp7r();
            res++;
        }

        // adjust for overage if needed
        // a pentagon base cell with a leading 4 digit requires special handling
        boolean pentLeading4 = (BaseCells.isBaseCellPentagon(baseCell) && h3LeadingNonZeroDigit(h3) == 4);
        if (fijk.adjustOverageClassII(res, pentLeading4, false) != FaceIJK.Overage.NO_OVERAGE) {
            // if the base cell is a pentagon we have the potential for secondary
            // overages
            if (BaseCells.isBaseCellPentagon(baseCell)) {
                FaceIJK.Overage overage;
                do {
                    overage = fijk.adjustOverageClassII(res, false, false);
                } while (overage != FaceIJK.Overage.NO_OVERAGE);
            }

            if (res != H3Index.H3_get_resolution(h3)) {
                fijk.coord.upAp7r();
            }
        } else if (res != H3Index.H3_get_resolution(h3)) {
            fijk.coord = origIJK;
        }
        return fijk;
    }

    /**
     * Returns the highest resolution non-zero digit in an H3Index.
     * @param h The H3Index.
     * @return The highest resolution non-zero digit in the H3Index.
     */
    public static int h3LeadingNonZeroDigit(long h) {
        for (int r = 1; r <= H3Index.H3_get_resolution(h); r++) {
            final int dir = H3Index.H3_get_index_digit(h, r);
            if (dir != CoordIJK.Direction.CENTER_DIGIT.digit()) {
                return dir;
            }
        }
        // if we're here it's all 0's
        return CoordIJK.Direction.CENTER_DIGIT.digit();
    }

    /**
     * Convert an H3Index to the FaceIJK address on a specified icosahedral face.
     * @param h The H3Index.
     * @param fijk The FaceIJK address, initialized with the desired face
     *        and normalized base cell coordinates.
     * @return Returns 1 if the possibility of overage exists, otherwise 0.
     */
    private static boolean h3ToFaceIjkWithInitializedFijk(long h, FaceIJK fijk) {

        int res = H3Index.H3_get_resolution(h);

        // center base cell hierarchy is entirely on this face
        boolean possibleOverage = true;
        if (BaseCells.isBaseCellPentagon(H3Index.H3_get_base_cell(h)) == false
            && (res == 0 || (fijk.coord.i == 0 && fijk.coord.j == 0 && fijk.coord.k == 0))) {
            possibleOverage = false;
        }

        for (int r = 1; r <= res; r++) {
            if (isResolutionClassIII(r)) {
                // Class III == rotate ccw
                fijk.coord.downAp7();
            } else {
                // Class II == rotate cw
                fijk.coord.downAp7r();
            }

            fijk.coord.neighbor(H3Index.H3_get_index_digit(h, r));
        }

        return possibleOverage;
    }

    /**
     * Rotate an H3Index 60 degrees clockwise.
     * @param h The H3Index.
     */
    private static long h3Rotate60cw(long h) {
        for (int r = 1, res = H3Index.H3_get_resolution(h); r <= res; r++) {
            h = H3Index.H3_set_index_digit(h, r, CoordIJK.rotate60cw(H3Index.H3_get_index_digit(h, r)));
        }
        return h;
    }
}
