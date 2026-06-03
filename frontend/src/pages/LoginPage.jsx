import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Zap, ArrowLeft, Eye, EyeOff, ShieldCheck, Wrench, User, LogIn } from 'lucide-react';
import { authApi } from '../services/api';
import './LoginPage.css';
import authIllustration from '../assets/auth-illustration.png';

export default function LoginPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const [isRegistering, setIsRegistering] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    role: 'USER'
  });

  const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;

  useEffect(() => {
    // Render official Google button on mount — only if client ID is configured
    if (googleClientId && window.google && window.google.accounts) {
      window.google.accounts.id.initialize({
        client_id: googleClientId,
        callback: handleGoogleCallback,
      });
      window.google.accounts.id.renderButton(
        document.getElementById('google-signin-button'),
        { 
          theme: 'outline', 
          size: 'large', 
          width: 320, 
          shape: 'pill',
          logo_alignment: 'left'
        }
      );
    }
  }, [isRegistering, googleClientId]);

  useEffect(() => {
    if (user) {
      const target = user.role === 'ADMIN' ? '/admin-dashboard' 
                  : user.role === 'TECHNICIAN' ? '/tech-dashboard' 
                  : '/user-dashboard';
      navigate(target, { replace: true });
    }
  }, [user, navigate]);

  if (user) return null;

  const handleGoogleCallback = async (response) => {
    setError('');
    setLoading(true);
    try {
      const res = await fetch('/api/v1/auth/google', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ googleToken: response.credential }),
      });
      
      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.message || 'Google authentication failed');
      }
      
      const data = await res.json();
      localStorage.setItem('token', data.token);
      window.location.href = '/dashboard';
    } catch (e) {
      setError(e.message);
      setLoading(false);
    }
  };

  const setDevRole = (role) => {
    localStorage.setItem('testRoleOverride', role);
    localStorage.setItem('token', 'dev-dummy-token'); // Fake token to trigger AuthContext
    window.location.href = '/dashboard';
  };

    const handleLocalLogin = async (e) => {
      e.preventDefault();
      const email = e.target.email.value;
      const password = e.target.password.value;
      
      setError('');
      setLoading(true);
      
      // Frontend Bypass for Demo Account
      if (email === 'admin@smartcampus.com' && password === 'admin123') {
        localStorage.setItem('token', 'mock-admin-token');
        localStorage.setItem('testRoleOverride', 'ADMIN');
        window.location.href = '/admin-dashboard';
        return;
      }

      try {
        const data = await authApi.login({ email, password });
        
        localStorage.setItem('token', data.token);
        localStorage.removeItem('testRoleOverride');

        // Role-based redirect using role from login response
        const role = data.user?.role;
        if (role === 'ADMIN') {
          window.location.href = '/admin-dashboard';
        } else if (role === 'TECHNICIAN') {
          window.location.href = '/tech-dashboard';
        } else {
          window.location.href = '/user-dashboard';
        }
      } catch (e) {
        setError(e.message);
      }
      setLoading(false);
    };

  const handleRegister = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');
    setLoading(true);

    try {
      await authApi.register(formData);
      setSuccessMsg('Account created! You can now sign in.');
      setIsRegistering(false);
      setFormData({ name: '', email: '', password: '', role: 'USER' });
    } catch (e) {
      setError(e.message);
    }
    setLoading(false);
  };

  return (
    <div className="auth-split-page">
      <div className="auth-left">
        <img src={authIllustration} alt="Smart Campus" className="auth-left__image" />
        <div className="auth-left__content">
          <h1 className="auth-left__title">Welcome Back.</h1>
          <p className="auth-left__subtitle">
            Sign in to manage campus resources, bookings, and maintenance seamlessly.
          </p>
        </div>
      </div>
      
      <div className="auth-right">
        <div className="auth-card">
          <h2 className="auth-card__title">{isRegistering ? 'Create Account' : 'Sign In'}</h2>
          <p className="auth-card__subtitle">
            {isRegistering ? 'Join as a Lecturer/Student or Technician.' : 'Enter your credentials to access your account.'}
          </p>
          
          {error && <div className="login-card__error">{error}</div>}
          {successMsg && (
            <div style={{ background: 'rgba(16,185,129,0.12)', border: '1px solid rgba(16,185,129,0.3)', color: '#34d399', padding: '0.75rem 1rem', borderRadius: '8px', fontSize: '0.875rem', textAlign: 'center', marginBottom: '1rem' }}>
              {successMsg}
            </div>
          )}



          {isRegistering ? (
            <form onSubmit={handleRegister}>
              <label className="auth-form-label">Full Name</label>
              <input type="text" required className="auth-form-input" placeholder="e.g. John Doe" value={formData.name} onChange={e => setFormData({...formData, name: e.target.value})} />
              
              <label className="auth-form-label">Email Address</label>
              <input type="email" required className="auth-form-input" placeholder="name@campus.edu" value={formData.email} onChange={e => setFormData({...formData, email: e.target.value})} />
              
              <label className="auth-form-label">Password</label>
              <div style={{ position: 'relative', marginBottom: '1.25rem' }}>
                <input
                  type={showPassword ? 'text' : 'password'}
                  required
                  className="auth-form-input"
                  placeholder="Minimum 6 characters"
                  value={formData.password}
                  onChange={e => setFormData({...formData, password: e.target.value})}
                  minLength={6}
                  style={{ marginBottom: 0, paddingRight: '2.8rem' }}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(v => !v)}
                  style={{
                    position: 'absolute', right: '0.75rem', top: '0.85rem',
                    background: 'none', border: 'none', cursor: 'pointer', color: '#64748b',
                    display: 'flex', alignItems: 'center', padding: 0
                  }}
                  tabIndex={-1}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>

              <label className="auth-form-label">Account Type</label>
              <select className="auth-form-input" value={formData.role} onChange={e => setFormData({...formData, role: e.target.value})} style={{ appearance: 'auto', backgroundColor: '#f8fafc', color: '#0f172a' }}>
                <option value="USER">Lecturer / Student</option>
                <option value="TECHNICIAN">Technician</option>
              </select>

              <button
                type="submit"
                className="auth-btn-primary"
                disabled={loading}
              >
                {loading ? 'Creating Account...' : 'Register Now'}
              </button>
            </form>
          ) : (
            <>
              <form onSubmit={handleLocalLogin}>
              <label className="auth-form-label">Email Address</label>
              <input type="email" name="email" required className="auth-form-input" placeholder="example@campus.edu" />
              
              <label className="auth-form-label">Password</label>
              <div style={{ position: 'relative' }}>
                <input
                  type={showPassword ? 'text' : 'password'}
                  name="password"
                  required
                  className="auth-form-input"
                  placeholder="••••••••"
                  style={{ paddingRight: '2.8rem' }}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(v => !v)}
                  style={{
                    position: 'absolute', right: '0.75rem', top: '0.85rem',
                    background: 'none', border: 'none', cursor: 'pointer', color: '#64748b',
                    display: 'flex', alignItems: 'center', padding: 0
                  }}
                  tabIndex={-1}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>

              <button
                type="submit"
                className="auth-btn-primary"
                disabled={loading}
              >
                {loading ? (
                  <>
                    <div className="animate-spin" style={{ width: '18px', height: '18px', border: '2px solid white', borderTopColor: 'transparent', borderRadius: '50%', marginRight: '8px' }} />
                    Signing In...
                  </>
                ) : (
                  <>
                    <LogIn size={20} style={{ marginRight: '8px' }} />
                    Sign In
                  </>
                )}
              </button>
            </form>
            {googleClientId && (
              <>
                <div className="auth-divider">or sign in with</div>
                <div 
                  id="google-signin-button" 
                  style={{ display: 'flex', justifyContent: 'center', marginBottom: '1rem' }}
                ></div>
              </>
            )}
          </>
          )}

          <div style={{ textAlign: 'center', fontSize: '0.9rem', color: '#64748b', marginTop: '1.5rem' }}>
            {isRegistering ? (
              <>Already have an account? <button onClick={() => { setIsRegistering(false); setError(''); }} style={{ color: '#4CA799', textDecoration: 'none', fontWeight: 600, background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>Sign in here</button></>
            ) : (
              <>Don't have an account? <button onClick={() => { setIsRegistering(true); setError(''); }} style={{ color: '#4CA799', textDecoration: 'none', fontWeight: 600, background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>Sign up free</button></>
            )}
          </div>

          <Link to="/" className="login-card__back" style={{ marginTop: '1.5rem', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '0.5rem' }}>
            <ArrowLeft size={16} />
            Back to Homepage
          </Link>
        </div>
      </div>
    </div>
  );
}
