// Supabase Client Configuration
export const SUPABASE_URL = "https://lwphgkfwdliuzcwgugzj.supabase.co";
export const SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imx3cGhna2Z3ZGxpdXpjd2d1Z3pqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTQ3ODA4MzIsImV4cCI6MjExMDM1NjgzMn0.ho25RHk5KJIpBecSVCl9OImWFDZxFPnfgEXX-q-zPKw";

export const supabase = window.supabase ? window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY) : null;
