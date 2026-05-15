package fr.neatmonster.nocheatplus.checks.moving.player;

/**
 * Diagnostic tag names shared by SurvivalFly model logic and console formatting.
 */
final class SurvivalFlyTags {

    private SurvivalFlyTags() {
    }

    // Elytra model collection: marks active-glide misses that are logged without applying VL/setback.
    static final String ELYTRA_MODEL_DATA_ONLY = "elytra_model_data_only";

    // Elytra state tags: identify which glide model is active before branch-specific diagnostics run.
    static final String MODE_ELYTRA_FIREWORK = "mode_elytra_firework";
    static final String MODE_ELYTRA_GLIDING = "mode_elytra_gliding";
    static final String GLIDE_FIREWORK_ACTIVE = "glide_firework_active";

    // Elytra prediction tags: classify which side of the model missed for later log triage.
    static final String GLIDE_VERTICAL_PREDICTION_MISS = "glide_vertical_prediction_miss";
    static final String GLIDE_VERTICAL_ACTUAL_ABOVE_MODEL = "glide_vertical_actual_above_model";
    static final String GLIDE_VERTICAL_ACTUAL_BELOW_MODEL = "glide_vertical_actual_below_model";
    static final String GLIDE_HORIZONTAL_PREDICTION_MISS = "glide_horizontal_prediction_miss";
    static final String GLIDE_HORIZONTAL_ACTUAL_ABOVE_MODEL = "glide_horizontal_actual_above_model";
    static final String GLIDE_HORIZONTAL_ACTUAL_BELOW_MODEL = "glide_horizontal_actual_below_model";
}
