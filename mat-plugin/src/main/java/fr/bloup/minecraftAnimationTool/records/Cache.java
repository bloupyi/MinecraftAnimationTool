package fr.bloup.minecraftAnimationTool.records;

import java.util.List;
import java.util.Map;

public record Cache(
        String name,
        List<Element> elements,
        List<Outliner> outliner,
        List<Animation> animations
) {
    public record Element(
            String name,
            boolean rescale,
            String render_order,
            double[] from,
            double[] to,
            double[] origin,
            String type,
            String uuid,
            double[] rotation,
            String material
    ) {}

    public record Outliner(
            String name,
            double[] origin,
            String uuid,
            boolean visibility,
            List<Object> children,
            double[] rotation
    ) {}

    public record Animation(
            String uuid,
            String name,
            String loop,
            boolean override,
            double length,
            int snapping,
            Map<String, Animator> animators
    ) {}

    public record Animator(
            String name,
            String type,
            List<Keyframe> keyframes
    ) {}

    public record Keyframe(
            String channel,
            List<Map<String, String>> data_points,
            String uuid,
            double time,
            int color,
            String interpolation,
            String easing,
            List<String> easingArgs
    ) {}
}