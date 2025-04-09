import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/auth.dart';
import 'api_service.dart';

class AuthService extends ChangeNotifier {
  static const _tokenKey = 'auth_token';
  final ApiService _api;
  UserProfile? _currentUser;
  bool _isLoading = false;

  AuthService(this._api);

  UserProfile? get currentUser => _currentUser;
  bool get isAuthenticated => _currentUser != null;
  bool get isLoading => _isLoading;

  Future<void> tryAutoLogin() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString(_tokenKey);
    if (token != null) {
      _api.setToken(token);
    }
  }

  Future<void> login(String email, String password) async {
    _isLoading = true;
    notifyListeners();
    try {
      final response = await _api.post('/auth/login', {'email': email, 'password': password});
      final auth = AuthResponse.fromJson(response);
      await _saveToken(auth.accessToken);
      _currentUser = auth.user;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> register(String displayName, String email, String password) async {
    _isLoading = true;
    notifyListeners();
    try {
      final response = await _api.post('/auth/register', {
        'displayName': displayName,
        'email': email,
        'password': password,
      });
      final auth = AuthResponse.fromJson(response);
      await _saveToken(auth.accessToken);
      _currentUser = auth.user;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> logout() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_tokenKey);
    _api.clearToken();
    _currentUser = null;
    notifyListeners();
  }

  Future<void> _saveToken(String token) async {
    _api.setToken(token);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_tokenKey, token);
  }
}