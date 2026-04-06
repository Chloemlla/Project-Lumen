import 'package:flutter/material.dart';
import 'package:project_lumen/features/settings/domain/models/app_settings.dart';
import 'package:project_lumen/shared/widgets/app_card.dart';
import 'package:project_lumen/shared/widgets/app_section_title.dart';
import 'package:project_lumen/shared/widgets/app_switch_tile.dart';

class NotificationSettingsSection extends StatelessWidget {
  const NotificationSettingsSection({super.key, required this.settings});

  final AppSettings settings;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const AppSectionTitle('通知设置'),
          AppSwitchTile(
            title: '预提醒通知',
            value: settings.preAlertEnabled,
            onChanged: (_) {},
          ),
          AppSwitchTile(
            title: '到点自动开始休息',
            value: settings.timeoutAutoBreak,
            onChanged: (_) {},
          ),
          Text(
            '通知调度接口已预留，当前先走本地骨架实现。',
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ),
    );
  }
}
