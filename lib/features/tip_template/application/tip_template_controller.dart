import 'package:flutter_riverpod/legacy.dart';
import 'package:project_lumen/app/bootstrap.dart';
import 'package:project_lumen/features/tip_template/domain/models/tip_template.dart';
import 'package:project_lumen/features/tip_template/domain/repositories/tip_template_repository.dart';

final tipTemplateControllerProvider =
    StateNotifierProvider<TipTemplateController, List<TipTemplate>>((ref) {
      final controller = TipTemplateController(
        ref.watch(tipTemplateRepositoryProvider),
      );
      controller.load();
      return controller;
    });

class TipTemplateController extends StateNotifier<List<TipTemplate>> {
  TipTemplateController(this._repository) : super(const []);

  final TipTemplateRepository _repository;

  Future<void> load() async {
    state = await _repository.getTemplates();
  }

  Future<void> refresh() => load();

  Future<void> updateTemplate(TipTemplate template) async {
    await _repository.updateTemplate(template);
    await load();
  }
}
