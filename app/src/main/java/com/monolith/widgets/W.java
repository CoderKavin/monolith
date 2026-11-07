package com.monolith.widgets;

/** One tiny provider per widget; Android needs a distinct receiver class for each. */
public final class W {

    private W() {}

    public static class Display extends Base { @Override int kind() { return Render.DISPLAY; } }
    public static class Centred extends Base { @Override int kind() { return Render.CENTRED; } }
    public static class Card extends Base { @Override int kind() { return Render.CARD; } }
    public static class Week extends Base { @Override int kind() { return Render.WEEK; } }
    public static class Screen extends Base { @Override int kind() { return Render.SCREEN; } }
    public static class Month extends Base { @Override int kind() { return Render.MONTH; } }
    public static class Battery extends Base { @Override int kind() { return Render.BATTERY; } }
    public static class ScreenSmall extends Base { @Override int kind() { return Render.SCREEN_SMALL; } }

    /** Indexed by Render kind. */
    static final Class<?>[] ALL = {
            Display.class, Centred.class, Card.class, Week.class,
            Screen.class, Month.class, Battery.class, ScreenSmall.class
    };
}
