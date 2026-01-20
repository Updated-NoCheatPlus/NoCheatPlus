/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.utilities.collision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShapeUtils {
    // Fixed-point quantization
    public static final int FP_SCALE = 1 << 12; // 4096 (safe for 0.1875, 1/16, etc)

    private static int fp(double v) {
        return (int) Math.round(v * FP_SCALE);
    }
    
    private static long keyYZ(int minY, int maxY, int minZ, int maxZ) {
        return (((long)minY) << 48)
             | (((long)maxY) << 32)
             | (((long)minZ) << 16)
             | ((long)maxZ);
    }

    private static long keyXZ(int minX, int maxX, int minZ, int maxZ) {
        return (((long)minX) << 48)
             | (((long)maxX) << 32)
             | (((long)minZ) << 16)
             | ((long)maxZ);
    }

    private static long keyXY(int minX, int maxX, int minY, int maxY) {
        return (((long)minX) << 48)
             | (((long)maxX) << 32)
             | (((long)minY) << 16)
             | ((long)maxY);
    }
    
    private static double[] mergeAxis(double[] in, int axis) {
        // axis: 0 = X, 1 = Y, 2 = Z

        Map<Long, ArrayList<Integer>> groups = new HashMap<>();

        int boxCount = in.length / 6;

        // --- group boxes ---
        for (int i = 0; i < boxCount; i++) {
            int o = i * 6;

            int minX = fp(in[o]);
            int minY = fp(in[o + 1]);
            int minZ = fp(in[o + 2]);
            int maxX = fp(in[o + 3]);
            int maxY = fp(in[o + 4]);
            int maxZ = fp(in[o + 5]);

            long key;
            if (axis == 0) key = keyYZ(minY, maxY, minZ, maxZ);
            else if (axis == 1) key = keyXZ(minX, maxX, minZ, maxZ);
            else key = keyXY(minX, maxX, minY, maxY);

            groups.computeIfAbsent(key, k -> new ArrayList<Integer>()).add(i);
        }

        ArrayList<Double> out = new ArrayList<Double>();

        // --- merge inside each group ---
        for (ArrayList<Integer> list : groups.values()) {
            list.sort((a, b) -> {
                int oa = a * 6;
                int ob = b * 6;
                return Double.compare(in[oa + axis], in[ob + axis]);
            });

            int first = list.get(0);
            int fo = first * 6;

            double minX = in[fo];
            double minY = in[fo + 1];
            double minZ = in[fo + 2];
            double maxX = in[fo + 3];
            double maxY = in[fo + 4];
            double maxZ = in[fo + 5];

            for (int i = 1; i < list.size(); i++) {
                int idx = list.get(i);
                int o = idx * 6;

                boolean touch;
                if (axis == 0) touch = Math.abs(maxX - in[o]) < 1e-9;
                else if (axis == 1) touch = Math.abs(maxY - in[o + 1]) < 1e-9;
                else touch = Math.abs(maxZ - in[o + 2]) < 1e-9;

                if (touch) {
                    // extend
                    if (axis == 0) maxX = in[o + 3];
                    else if (axis == 1) maxY = in[o + 4];
                    else maxZ = in[o + 5];
                } else {
                    // flush
                    out.add(minX); out.add(minY); out.add(minZ);
                    out.add(maxX); out.add(maxY); out.add(maxZ);

                    minX = in[o];
                    minY = in[o + 1];
                    minZ = in[o + 2];
                    maxX = in[o + 3];
                    maxY = in[o + 4];
                    maxZ = in[o + 5];
                }
            }

            out.add(minX); out.add(minY); out.add(minZ);
            out.add(maxX); out.add(maxY); out.add(maxZ);
        }
        double[] fres = new double[out.size()];
        for (int i= 0; i < out.size(); i++) {
            fres[i] = out.get(i);
        }
        return fres;
    }
    
    public static double[] merge(double[] a, double[] b) {
        return optimize(add(a,b));
    }

    public static double[] optimize(double[] shape) {
        if (shape.length <= 6) {
            return shape; // already minimal
        }

        double[] prev;
        double[] cur = shape;

        do {
            prev = cur;
            cur = mergeAxis(cur, 0); // X
            cur = mergeAxis(cur, 1); // Y
            cur = mergeAxis(cur, 2); // Z
        } while (cur.length < prev.length);

        return cur;
    }

    public static List<Axis> getRelative(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                   double tminX, double tminY, double tminZ, double tmaxX, double tmaxY, double tmaxZ) {
        final List<Axis> list = new ArrayList<Axis>();
        if (minX == tmaxX || maxX == tminX) {
            list.add(Axis.X_AXIS);
        }
        if (minY == tmaxY || maxY == tminY) {
            list.add(Axis.Y_AXIS);
        }
        if (minZ == tmaxZ || maxZ == tminZ) {
            list.add(Axis.Z_AXIS);
        }
        return list;
    }

    public static boolean sameshape(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double tminX,
                              double tminY, double tminZ, double tmaxX, double tmaxY, double tmaxZ) {
        final double dx = maxX - minX;
        final double dy = maxY - minY;
        final double dz = maxZ - minZ;
        final double tdx = tmaxX - tminX;
        final double tdy = tmaxY - tminY;
        final double tdz = tmaxZ - tminZ;
        return dx == tdx && dy == tdy && dz == tdz;
    }
    
    /**
     * Concatenates two {@code double[]} arrays into a single array.
     * <p>
     * The contents of {@code array1} are placed first, followed by the contents
     * of {@code array2}. The resulting array has a length equal to the sum of
     * the lengths of the two input arrays.
     * </p>
     *
     * <pre>
     * Example:
     * array1 = {1.0, 2.0}
     * array2 = {3.0, 4.0}
     * result = {1.0, 2.0, 3.0, 4.0}
     * </pre>
     *
     * @param array1 the first array, may be empty but not {@code null}
     * @param array2 the second array, may be empty but not {@code null}
     * @return a new array containing all elements of {@code array1} followed by
     *         all elements of {@code array2}
     */
    public static double[] add(final double[] array1, final double[] array2) {
        final double[] newArray = new double[array1.length + array2.length];
        System.arraycopy(array1, 0, newArray, 0, array1.length);
        System.arraycopy(array2, 0, newArray, array1.length, array2.length);
        return newArray;
    }
}
