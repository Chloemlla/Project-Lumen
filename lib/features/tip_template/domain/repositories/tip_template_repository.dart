import 'package:project_lumen/core/storage/db/daos/tip_templates_dao.dart';
import 'package:project_lumen/features/tip_template/domain/models/tip_template.dart';

abstract class TipTemplateRepository {
  Future<void> seedBuiltIns();

  Future<List<TipTemplate>> getTemplates();

  Future<void> updateTemplate(TipTemplate template);
}

class LocalTipTemplateRepository implements TipTemplateRepository {
  LocalTipTemplateRepository(this._tipTemplatesDao);

  final TipTemplatesDao _tipTemplatesDao;

  @override
  Future<List<TipTemplate>> getTemplates() async {
    final rows = await _tipTemplatesDao.fetchAll();
    return rows.map(TipTemplate.fromMap).toList(growable: false);
  }

  @override
  Future<void> seedBuiltIns() async {
    final total = await _tipTemplatesDao.count();
    if (total > 0) {
      return;
    }

    final now = DateTime.now().toIso8601String();
    final templates = [
      {
        'name': 'Calm Horizon',
        'is_builtin': 1,
        'background_type': 'solid',
        'background_value': '#14746F',
        'primary_color': '#F4A261',
        'title_text': '抬头放松一下',
        'subtitle_text': '让视线离开屏幕，给眼睛 20 秒缓冲',
        'show_skip_button': 1,
        'sort_order': 1,
        'created_at': now,
        'updated_at': now,
      },
      {
        'name': 'Soft Focus',
        'is_builtin': 1,
        'background_type': 'solid',
        'background_value': '#355070',
        'primary_color': '#EAAC8B',
        'title_text': '专注结束，开始休息',
        'subtitle_text': '深呼吸，眺望远处，再回来继续工作',
        'show_skip_button': 1,
        'sort_order': 2,
        'created_at': now,
        'updated_at': now,
      },
      {
        'name': 'Sunrise Loop',
        'is_builtin': 1,
        'background_type': 'solid',
        'background_value': '#BC6C25',
        'primary_color': '#FEFAE0',
        'title_text': '休息一下吧',
        'subtitle_text': '短暂离屏，也是在保护长期效率',
        'show_skip_button': 0,
        'sort_order': 3,
        'created_at': now,
        'updated_at': now,
      },
    ];

    for (final template in templates) {
      await _tipTemplatesDao.insert(template);
    }
  }

  @override
  Future<void> updateTemplate(TipTemplate template) {
    return _tipTemplatesDao.updateById(template.id, {
      'name': template.name,
      'background_type': template.backgroundType,
      'background_value': template.backgroundValue,
      'primary_color': template.primaryColor,
      'title_text': template.titleText,
      'subtitle_text': template.subtitleText,
      'image_path': template.imagePath,
      'show_skip_button': template.showSkipButton ? 1 : 0,
      'layout_json': template.layout.rawJson,
      'sort_order': template.sortOrder,
      'updated_at': DateTime.now().toIso8601String(),
    });
  }
}
