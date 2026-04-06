import 'package:flutter/material.dart';
import 'package:project_lumen/features/tip_template/domain/models/tip_template.dart';

class TemplateSelector extends StatelessWidget {
  const TemplateSelector({
    super.key,
    required this.templates,
    required this.selectedId,
    required this.onSelected,
  });

  final List<TipTemplate> templates;
  final int? selectedId;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: templates
          .map(
            (template) => ListTile(
              onTap: () => onSelected(template.id),
              contentPadding: EdgeInsets.zero,
              leading: Icon(
                template.id == selectedId
                    ? Icons.radio_button_checked_rounded
                    : Icons.radio_button_off_rounded,
              ),
              title: Text(template.name),
              subtitle: Text(template.subtitleText),
            ),
          )
          .toList(growable: false),
    );
  }
}
