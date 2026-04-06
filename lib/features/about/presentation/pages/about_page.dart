import 'package:flutter/material.dart';
import 'package:project_lumen/shared/widgets/app_card.dart';
import 'package:project_lumen/shared/widgets/app_scaffold.dart';

class AboutPage extends StatelessWidget {
  const AboutPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const AppScaffold(
      title: '关于',
      location: '/settings',
      body: _AboutContent(),
    );
  }
}

class _AboutContent extends StatelessWidget {
  const _AboutContent();

  @override
  Widget build(BuildContext context) {
    return ListView(
      children: const [
        AppCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Project-Lumen'),
              SizedBox(height: 8),
              Text('v1 Foundation'),
              SizedBox(height: 12),
              Text('用眼提醒、番茄钟、统计与模板系统的 Flutter 手机端基础框架。'),
            ],
          ),
        ),
      ],
    );
  }
}
