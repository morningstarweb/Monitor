fn format_oauth_launcher_html(auth_url: &str) -> String {
    format!(
        r#"<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Opening Google Sign-In...</title>
<style>
body {{ background: #0b0f19; color: #f8fafc; font-family: system-ui, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }}
.card {{ background: #131b2e; border: 1px solid #1e293b; border-radius: 16px; padding: 32px 48px; text-align: center; }}
h2 {{ font-size: 20px; color: #38bdf8; margin: 0 0 10px; }}
p {{ font-size: 14px; color: #94a3b8; margin: 0; }}
</style>
</head>
<body>
<div class="card">
<h2>Opening Google Sign-In...</h2>
<p>Redirecting you to Google Accounts.</p>
</div>
<script>
window.location.replace("{auth_url}");
</script>
</body>
</html>"#
    )
}
