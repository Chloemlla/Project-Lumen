import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:project_lumen/features/settings/application/settings_controller.dart';
import 'package:project_lumen/features/tip_template/application/tip_template_controller.dart';
import 'package:project_lumen/features/tip_template/presentation/widgets/template_preview.dart';
import 'package:project_lumen/features/tip_template/presentation/widgets/template_selector.dart';
import 'package:project_lumen/shared/widgets/app_empty_view.dart';
import 'package:project_lumen/shared/widgets/app_scaffold.dart';

class TipTemplatePage extends ConsumerWidget {
  const TipTemplatePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final templates = ref.watch(tipTemplateControllerProvider);
    final settings = ref.watch(settingsControllerProvider);

    return AppScaffold(
      title: '提醒模板',
      location: '/settings',
      actions: [
        IconButton(
          onPressed: () => context.go('/templates/preview'),
          icon: const Icon(Icons.visibility_outlined),
        ),
      ],
      body: templates.isEmpty
          ? const AppEmptyView(title: '暂无模板', message: '请先完成内置模板初始化。')
          : ListView(
              children: [
                TemplateSelector(
                  templates: templates,
                  selectedId: settings.activeTipTemplateId,
                  onSelected: (value) {
                    ref
                        .read(settingsControllerProvider.notifier)
                        .selectTemplate(value);
                  },
                ),
                const SizedBox(height: 16),
                TemplatePreview(
                  template: templates.firstWhere(
                    (item) => item.id == settings.activeTipTemplateId,
                    orElse: () => templates.first,
                  ),
                ),
              ],
            ),
    );
  }
}
