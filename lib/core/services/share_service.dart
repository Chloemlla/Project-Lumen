import 'package:share_plus/share_plus.dart';

class ShareService {
  Future<void> shareText(String text, {String? subject}) async {
    await SharePlus.instance.share(ShareParams(text: text, subject: subject));
  }
}
