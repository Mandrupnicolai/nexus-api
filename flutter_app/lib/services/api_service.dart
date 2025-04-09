import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/task.dart';
import '../models/auth.dart';

/// HTTP client for the NexusAPI REST backend.
///
/// Encapsulates all API communication in one place so:
/// - The base URL can be changed for different environments
/// - Auth headers are applied consistently
/// - Error handling is centralised
/// - Screens never deal with raw HTTP concerns
class ApiService {
  static const String _baseUrl = 'http://localhost:8080/api/v1';

  // ---------------------------------------------------------------------------
  // Authentication
  // ---------------------------------------------------------------------------

  /// Registers a new user account.
  ///
  /// Returns an [AuthResponse] containing the JWT token on success.
  /// Throws [ApiException] on failure (e.g. duplicate email → 409).
  Future<AuthResponse> register({
    required String email,
    required String username,
    required String password,
    String? fullName,
  }) async {
    final response = await http.post(
      Uri.parse('$_baseUrl/auth/register'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'email': email,
        'username': username,
        'password': password,
        if (fullName != null) 'fullName': fullName,
      }),
    );
    return _handleAuthResponse(response);
  }

  /// Authenticates an existing user and returns a JWT token.
  Future<AuthResponse> login({
    required String email,
    required String password,
  }) async {
    final response = await http.post(
      Uri.parse('$_baseUrl/auth/login'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'email': email, 'password': password}),
    );
    return _handleAuthResponse(response);
  }

  // ---------------------------------------------------------------------------
  // Tasks
  // ---------------------------------------------------------------------------

  /// Fetches a paginated list of tasks for a project.
  ///
  /// [token]     — JWT Bearer token from [AuthService]
  /// [projectId] — the project to query
  /// [page]      — zero-based page number
  /// [size]      — items per page
  Future<List<Task>> getTasks({
    required String token,
    required String projectId,
    int page = 0,
    int size = 20,
  }) async {
    final uri = Uri.parse('$_baseUrl/tasks').replace(queryParameters: {
      'projectId': projectId,
      'page': page.toString(),
      'size': size.toString(),
      'sort': 'position,asc',
    });

    final response = await http.get(
      uri,
      headers: _authHeaders(token),
    );

    _checkStatus(response);
    final body = jsonDecode(response.body) as Map<String, dynamic>;
    final content = body['content'] as List<dynamic>;
    return content.map((e) => Task.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Creates a new task.
  Future<Task> createTask({
    required String token,
    required String title,
    required String projectId,
    String? description,
    String? assigneeId,
    String? priority,
    String? dueDate,
  }) async {
    final response = await http.post(
      Uri.parse('$_baseUrl/tasks'),
      headers: _authHeaders(token)..['Content-Type'] = 'application/json',
      body: jsonEncode({
        'title': title,
        'projectId': projectId,
        if (description != null) 'description': description,
        if (assigneeId != null) 'assigneeId': assigneeId,
        if (priority != null) 'priority': priority,
        if (dueDate != null) 'dueDate': dueDate,
      }),
    );

    _checkStatus(response, expectedStatus: 201);
    return Task.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  /// Updates task fields (partial update — only provided fields are changed).
  Future<Task> updateTask({
    required String token,
    required String taskId,
    String? title,
    String? status,
    String? priority,
    String? assigneeId,
  }) async {
    final body = <String, dynamic>{};
    if (title != null) body['title'] = title;
    if (status != null) body['status'] = status;
    if (priority != null) body['priority'] = priority;
    if (assigneeId != null) body['assigneeId'] = assigneeId;

    final response = await http.patch(
      Uri.parse('$_baseUrl/tasks/$taskId'),
      headers: _authHeaders(token)..['Content-Type'] = 'application/json',
      body: jsonEncode(body),
    );

    _checkStatus(response);
    return Task.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  /// Deletes a task (soft delete on the server).
  Future<void> deleteTask({
    required String token,
    required String taskId,
  }) async {
    final response = await http.delete(
      Uri.parse('$_baseUrl/tasks/$taskId'),
      headers: _authHeaders(token),
    );
    _checkStatus(response, expectedStatus: 204);
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  Map<String, String> _authHeaders(String token) => {
    'Authorization': 'Bearer $token',
    'Content-Type': 'application/json',
  };

  AuthResponse _handleAuthResponse(http.Response response) {
    _checkStatus(response, expectedStatus: response.statusCode < 300 ? response.statusCode : 200);
    return AuthResponse.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  /// Throws [ApiException] if the response status does not match [expectedStatus].
  void _checkStatus(http.Response response, {int expectedStatus = 200}) {
    if (response.statusCode != expectedStatus && response.statusCode >= 400) {
      final body = jsonDecode(response.body) as Map<String, dynamic>?;
      throw ApiException(
        statusCode: response.statusCode,
        message: body?['message'] as String? ?? 'Unknown error',
      );
    }
  }
}

/// Represents an error response from the NexusAPI backend.
class ApiException implements Exception {
  final int statusCode;
  final String message;

  const ApiException({required this.statusCode, required this.message});

  @override
  String toString() => 'ApiException($statusCode): $message';

  bool get isUnauthorized => statusCode == 401;
  bool get isForbidden => statusCode == 403;
  bool get isNotFound => statusCode == 404;
  bool get isConflict => statusCode == 409;
}
