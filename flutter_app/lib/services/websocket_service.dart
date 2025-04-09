import 'package:stomp_dart_client/stomp.dart';
import 'package:stomp_dart_client/stomp_config.dart';
import 'package:stomp_dart_client/stomp_frame.dart';

class WebSocketService {
  StompClient? _client;
  final String baseUrl;
  final String token;

  WebSocketService({required this.baseUrl, required this.token});

  void connect({required void Function(StompFrame) onTaskEvent, required String projectId}) {
    _client = StompClient(
      config: StompConfig.sockJS(
        url: '/ws',
        onConnect: (frame) {
          _client!.subscribe(
            destination: '/topic/projects//tasks',
            callback: onTaskEvent,
          );
        },
        stompConnectHeaders: {'Authorization': 'Bearer '},
        webSocketConnectHeaders: {'Authorization': 'Bearer '},
      ),
    );
    _client!.activate();
  }

  void disconnect() {
    _client?.deactivate();
  }
}