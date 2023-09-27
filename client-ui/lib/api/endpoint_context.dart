import 'dart:io';

abstract class EndpointContext {
  SecurityContext get();
}

class DefaultHttpsContext extends EndpointContext {
  final SecurityContext _context = SecurityContext.defaultContext;

  @override
  SecurityContext get() => _context;
}

class CustomHttpsContext extends EndpointContext {
  CustomHttpsContext({
    required String certificatePath,
    required String certificatePassword,
  }) {
    final context = SecurityContext();
    context.setTrustedCertificates(certificatePath, password: certificatePassword);
    _context = context;
  }

  late SecurityContext _context;

  @override
  SecurityContext get() => _context;
}
