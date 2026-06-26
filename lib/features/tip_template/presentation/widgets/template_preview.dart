import 'package:flutter/material.dart';
import 'package:project_lumen/features/tip_template/domain/models/tip_template.dart';

class TemplatePreview extends StatelessWidget {
  const TemplatePreview({super.key, required this.template, this.onStartBreak});

  final TipTemplate template;
  final VoidCallback? onStartBreak;

  @override
  Widget build(BuildContext context) {
    final backgroundColor = Color(
      int.parse(template.backgroundValue.replaceFirst('#', '0xFF')),
    );
    final primaryColor = Color(
      int.parse(template.primaryColor.replaceFirst('#', '0xFF')),
    );

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            template.titleText,
            style: Theme.of(
              context,
            ).textTheme.headlineMedium?.copyWith(color: primaryColor),
          ),
          const SizedBox(height: 10),
          Text(
            template.subtitleText,
            style: Theme.of(
              context,
            ).textTheme.bodyLarge?.copyWith(color: Colors.white),
          ),
          if (onStartBreak != null) ...[
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed: onStartBreak,
                icon: const Icon(Icons.self_improvement_rounded),
                label: const Text('开始休息'),
                style: FilledButton.styleFrom(
                  backgroundColor: primaryColor,
                  foregroundColor: primaryColor.computeLuminance() > 0.5
                      ? Colors.black
                      : Colors.white,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
