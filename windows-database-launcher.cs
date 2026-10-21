using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Security.Principal;

internal static class WindowsDatabaseLauncher
{
    private static int Main()
    {
        try
        {
            using (WindowsIdentity identity = WindowsIdentity.GetCurrent())
            {
                if (!new WindowsPrincipal(identity).IsInRole(WindowsBuiltInRole.Administrator))
                {
                    ProcessStartInfo elevated = new ProcessStartInfo(Assembly.GetExecutingAssembly().Location);
                    elevated.UseShellExecute = true;
                    elevated.Verb = "runas";
                    Process.Start(elevated);
                    return 0;
                }
            }

            string tempDirectory = Path.Combine(Path.GetTempPath(), "FisioDatabase-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(tempDirectory);
            try
            {
                string scriptPath = Path.Combine(tempDirectory, "install-fisio-windows.ps1");
                using (Stream resource = Assembly.GetExecutingAssembly().GetManifestResourceStream("FisioDatabaseScript"))
                {
                    if (resource == null) throw new InvalidOperationException("Script di configurazione assente dal pacchetto.");
                    using (FileStream file = File.Create(scriptPath)) resource.CopyTo(file);
                }

                string powershell = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),
                    @"WindowsPowerShell\v1.0\powershell.exe");
                ProcessStartInfo setup = new ProcessStartInfo(powershell);
                setup.Arguments = "-NoProfile -STA -ExecutionPolicy Bypass -File \"" + scriptPath + "\" -DatabaseOnly";
                setup.UseShellExecute = false;
                using (Process process = Process.Start(setup))
                {
                    process.WaitForExit();
                    if (process.ExitCode != 0)
                    {
                        Console.Error.WriteLine("Configurazione non completata. Codice: " + process.ExitCode);
                        Console.WriteLine("Premi INVIO dopo aver letto l'errore sopra.");
                        Console.ReadLine();
                        return process.ExitCode;
                    }
                }
            }
            finally
            {
                Directory.Delete(tempDirectory, true);
            }

            Console.WriteLine("MySQL e database pronti. Ora esegui Fisio e Sports - installer.exe.");
            Console.WriteLine("Premi INVIO per chiudere.");
            Console.ReadLine();
            return 0;
        }
        catch (Exception error)
        {
            Console.Error.WriteLine("Errore: " + error.Message);
            Console.WriteLine("Premi INVIO dopo aver letto l'errore sopra.");
            Console.ReadLine();
            return 1;
        }
    }
}
