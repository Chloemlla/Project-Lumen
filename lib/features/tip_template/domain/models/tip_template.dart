import 'package:project_lumen/features/tip_template/domain/models/tip_template_layout.dart';

class TipTemplate {
  const TipTemplate({
    required this.id,
    required this.name,
    required this.isBuiltin,
    required this.backgroundType,
    required this.backgroundValue,
    required this.primaryColor,
    required this.titleText,
    required this.subtitleText,
    required this.imagePath,
    required this.showSkipButton,
    required this.layout,
    required this.sortOrder,
    required this.createdAt,
    required this.updatedAt,
  });

  final int id;
  final bool isBuiltin;
  final String name;
  final String backgroundType;
  final String backgroundValue;
  final String primaryColor;
  final String titleText;
  final String subtitleText;
  final String? imagePath;
  final bool showSkipButton;
  final TipTemplateLayout layout;
  final int sortOrder;
  final DateTime createdAt;
  final DateTime updatedAt;

  factory TipTemplate.fromMap(Map<String, Object?> map) {
    return TipTemplate(
      id: (map['id'] as int?) ?? 0,
      name: (map['name'] as String?) ?? '',
      isBuiltin: ((map['is_builtin'] as int?) ?? 0) == 1,
      backgroundType: (map['background_type'] as String?) ?? 'solid',
      backgroundValue: (map['background_value'] as String?) ?? '#14746F',
      primaryColor: (map['primary_color'] as String?) ?? '#14746F',
      titleText: (map['title_text'] as String?) ?? '休息一下吧',
      subtitleText: (map['subtitle_text'] as String?) ?? '请将视线转向远处',
      imagePath: map['image_path'] as String?,
      showSkipButton: ((map['show_skip_button'] as int?) ?? 1) == 1,
      layout: TipTemplateLayout(rawJson: map['layout_json'] as String?),
      sortOrder: (map['sort_order'] as int?) ?? 0,
      createdAt:
          DateTime.tryParse((map['created_at'] as String?) ?? '') ??
          DateTime.fromMillisecondsSinceEpoch(0),
      updatedAt:
          DateTime.tryParse((map['updated_at'] as String?) ?? '') ??
          DateTime.fromMillisecondsSinceEpoch(0),
    );
  }
}
