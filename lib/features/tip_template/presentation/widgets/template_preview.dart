import 'package:flutter/material.dart';
import 'package:project_lumen/features/tip_template/domain/models/tip_template.dart';

class TemplatePreview extends StatelessWidget {
  const TemplatePreview({super.key, required this.template});

  final TipTemplate template;

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
        borderRadius: BorderRadius.circular(28),
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
          const SizedBox(height: 20),
          FilledButton(
            onPressed: () {},
            style: FilledButton.styleFrom(backgroundColor: primaryColor),
            child: const Text('开始休息'),
          ),
        ],
      ),
    );
  }
}
