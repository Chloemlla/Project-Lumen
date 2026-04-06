import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:project_lumen/features/settings/application/settings_controller.dart';
import 'package:project_lumen/features/tip_template/application/tip_template_controller.dart';
import 'package:project_lumen/features/tip_template/presentation/widgets/template_preview.dart';
import 'package:project_lumen/shared/widgets/app_empty_view.dart';
import 'package:project_lumen/shared/widgets/app_scaffold.dart';

class TipTemplatePreviewPage extends ConsumerWidget {
  const TipTemplatePreviewPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final templates = ref.watch(tipTemplateControllerProvider);
    final settings = ref.watch(settingsControllerProvider);

    if (templates.isEmpty) {
      return const AppScaffold(
        title: '模板预览',
        location: '/settings',
        body: AppEmptyView(title: '暂无模板可预览', message: '请先在模板页选择一个模板。'),
      );
    }

    final selected = templates.firstWhere(
      (item) => item.id == settings.activeTipTemplateId,
      orElse: () => templates.first,
    );

    return AppScaffold(
      title: '模板预览',
      location: '/settings',
      body: Center(child: TemplatePreview(template: selected)),
    );
  }
}
