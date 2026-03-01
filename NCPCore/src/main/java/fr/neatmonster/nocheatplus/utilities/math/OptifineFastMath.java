package fr.neatmonster.nocheatplus.utilities.math;

/**
 * Encapsulates the fast math trigonometric tables from Optifine.
 */
public class OptifineFastMath {
    
    ///
    /// Not only does Optifine uses a different trigonometric table, but it's also not even consistent across mod versions, as it got changed at some point...
    /// MODERN -----
    private static final float[] SIN = new float[4096];
    
    static {
        for (int i = 0; i < 4096; i++) {
            SIN[i] = roundToFloat(StrictMath.sin(i * Math.PI * 2d / 4096d));
        }
    }
    
    public static float fastSin(float value) {
        return SIN[(int) (value * 651.8986f) & 4095];
    }
    
    public static float fastCos(float value) {
        return SIN[(int) (value * 651.8986f + 1024f) & 4095];
    }
    
    private static float roundToFloat(double value) {
        return (float) ((double) Math.round(value * 1.0E8d) / 1.0E8d);
    }
    
    // LEGACY ----
    private static final float[] SIN_TABLE_FAST = new float[4096];
    
    static {
        for (int i = 0; i < 4096; ++i) {
            SIN_TABLE_FAST[i] = (float) Math.sin(((float) i + 0.5f) / 4096f * ((float) Math.PI * 2f));
        }
        
        for (int i = 0; i < 360; i += 90) {
            SIN_TABLE_FAST[(int) ((float) i * 11.377778f) & 4095] = (float) Math.sin(i * TrigUtil.toRadians);
        }
    }
    
    public static float legacyFastSin(float value) {
        return SIN_TABLE_FAST[(int) (value * 651.8986f) & 4095];
    }
    
    public static float legacyFastCos(float value) {
        return SIN_TABLE_FAST[(int) ((value + ((float) Math.PI / 2f)) * 651.8986f) & 4095];
    }
    
}
