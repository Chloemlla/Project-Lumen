class AppL10n {
  const AppL10n._(this.languageCode);

  final String languageCode;

  static AppL10n of(String languageCode) => AppL10n._(languageCode);

  String get appName => 'Project-Lumen';
}
