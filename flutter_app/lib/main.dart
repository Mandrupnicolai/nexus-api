import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'services/api_service.dart';
import 'services/auth_service.dart';
import 'services/websocket_service.dart';
import 'screens/login_screen.dart';
import 'screens/task_board_screen.dart';

/// Entry point for the NexusAPI Flutter client.
///
/// Demonstrates:
/// - Provider state management pattern
/// - JWT token handling and persistence
/// - Real-time WebSocket task updates via STOMP
/// - Clean separation of API, auth, and WebSocket concerns
void main() {
  runApp(const NexusApp());
}

class NexusApp extends StatelessWidget {
  const NexusApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        // ApiService is the foundation — other services depend on it
        Provider<ApiService>(create: (_) => ApiService()),
        ChangeNotifierProxyProvider<ApiService, AuthService>(
          create: (ctx) => AuthService(ctx.read<ApiService>()),
          update: (_, api, prev) => prev ?? AuthService(api),
        ),
        ChangeNotifierProxyProvider<AuthService, WebSocketService>(
          create: (ctx) => WebSocketService(ctx.read<AuthService>()),
          update: (_, auth, prev) => prev ?? WebSocketService(auth),
        ),
      ],
      child: MaterialApp(
        title: 'NexusAPI',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(
            seedColor: const Color(0xFF4F46E5), // Indigo
            brightness: Brightness.light,
          ),
          useMaterial3: true,
          cardTheme: const CardThemeData(elevation: 0),
        ),
        home: const AuthGate(),
      ),
    );
  }
}

/// Decides whether to show the login screen or the main app
/// based on authentication state.
class AuthGate extends StatelessWidget {
  const AuthGate({super.key});

  @override
  Widget build(BuildContext context) {
    return Consumer<AuthService>(
      builder: (context, auth, _) {
        if (auth.isAuthenticated) {
          return const TaskBoardScreen();
        }
        return const LoginScreen();
      },
    );
  }
}
