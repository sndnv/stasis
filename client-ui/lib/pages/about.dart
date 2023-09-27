import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:flutter/material.dart';

class About extends StatelessWidget {
  const About({super.key});

  static const String name = 'stasis';

  static const String licenseHeader = 'This project is licensed under the Apache License, Version 2.0';

  static const String licenseCopyright = 'Copyright 2018 https://github.com/sndnv';

  static final String licenseInfo = '''
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
  '''
      .split('\n')
      .map((line) => line.trim())
      .join('\n');

  static const String licenseFooter = 'For more information, visit https://github.com/sndnv/stasis';

  @override
  Widget build(BuildContext context) {
    return buildPage<int>(
      of: () => Future.value(0),
      builder: (context, _) {
        final theme = Theme.of(context);

        final divider = Padding(
          padding: const EdgeInsets.symmetric(horizontal: 96.0),
          child: Divider(color: theme.primaryColor, thickness: 1.0),
        );

        return Column(
          mainAxisSize: MainAxisSize.max,
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Text(name, style: theme.textTheme.headlineSmall),
            divider,
            const Padding(padding: EdgeInsets.all(16.0), child: Text(licenseHeader)),
            Text(licenseCopyright, style: theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.bold)),
            Padding(
                padding: const EdgeInsets.all(16.0),
                child: Text(licenseInfo, textAlign: TextAlign.center, style: theme.textTheme.bodySmall)),
            divider,
            const Text(licenseFooter),
          ],
        );
      },
    );
  }
}
