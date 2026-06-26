import 'package:flutter/material.dart';

enum AppActionStyle { filled, tonal, outlined, text }

class AppAction {
  const AppAction({
    required this.label,
    required this.onPressed,
    this.icon,
    this.style = AppActionStyle.filled,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final AppActionStyle style;
}

class AppActionGroup extends StatelessWidget {
  const AppActionGroup({
    super.key,
    required this.actions,
    this.alignment = WrapAlignment.start,
    this.compactBreakpoint = 420,
  });

  final List<AppAction> actions;
  final WrapAlignment alignment;
  final double compactBreakpoint;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final useFullWidth = constraints.maxWidth < compactBreakpoint;
        final buttons = actions.map(_buildButton).toList(growable: false);

        if (useFullWidth) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (var index = 0; index < buttons.length; index++) ...[
                buttons[index],
                if (index != buttons.length - 1) const SizedBox(height: 12),
              ],
            ],
          );
        }

        return Wrap(
          spacing: 12,
          runSpacing: 12,
          alignment: alignment,
          children: buttons,
        );
      },
    );
  }

  Widget _buildButton(AppAction action) {
    final icon = action.icon == null ? null : Icon(action.icon);

    return switch (action.style) {
      AppActionStyle.filled =>
        icon == null
            ? FilledButton(
                onPressed: action.onPressed,
                child: Text(action.label),
              )
            : FilledButton.icon(
                onPressed: action.onPressed,
                icon: icon,
                label: Text(action.label),
              ),
      AppActionStyle.tonal =>
        icon == null
            ? FilledButton.tonal(
                onPressed: action.onPressed,
                child: Text(action.label),
              )
            : FilledButton.tonalIcon(
                onPressed: action.onPressed,
                icon: icon,
                label: Text(action.label),
              ),
      AppActionStyle.outlined =>
        icon == null
            ? OutlinedButton(
                onPressed: action.onPressed,
                child: Text(action.label),
              )
            : OutlinedButton.icon(
                onPressed: action.onPressed,
                icon: icon,
                label: Text(action.label),
              ),
      AppActionStyle.text =>
        icon == null
            ? TextButton(onPressed: action.onPressed, child: Text(action.label))
            : TextButton.icon(
                onPressed: action.onPressed,
                icon: icon,
                label: Text(action.label),
              ),
    };
  }
}
