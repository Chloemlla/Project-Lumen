import 'package:flutter/material.dart';
import 'package:project_lumen/shared/widgets/app_action_group.dart';

class BreakActionButtons extends StatelessWidget {
  const BreakActionButtons({
    super.key,
    required this.onStartBreak,
    required this.onSkip,
    required this.disableSkip,
    required this.isResting,
  });

  final VoidCallback onStartBreak;
  final VoidCallback onSkip;
  final bool disableSkip;
  final bool isResting;

  @override
  Widget build(BuildContext context) {
    if (isResting) {
      return AppActionGroup(
        alignment: WrapAlignment.center,
        actions: [
          AppAction(
            label: '提前结束休息',
            icon: Icons.check_circle_outline_rounded,
            onPressed: onStartBreak,
          ),
        ],
      );
    }

    return AppActionGroup(
      alignment: WrapAlignment.center,
      actions: [
        AppAction(
          label: '开始休息',
          icon: Icons.self_improvement_rounded,
          onPressed: onStartBreak,
        ),
        AppAction(
          label: '跳过本次',
          icon: Icons.skip_next_rounded,
          onPressed: disableSkip ? null : onSkip,
          style: AppActionStyle.outlined,
        ),
      ],
    );
  }
}
