import 'package:flutter_test/flutter_test.dart';
import 'package:project_lumen/core/utils/duration_x.dart';

void main() {
  test('DurationX formats mm:ss', () {
    expect(const Duration(minutes: 2, seconds: 5).mmssLabel, '02:05');
  });
}
