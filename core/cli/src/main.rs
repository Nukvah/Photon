//! photon-cli: runs the Photon pipeline over a folder of photos on a desktop.
//!
//!     photon-cli [--preset small|standard|sharp] <input-dir> <output-dir>
//!
//! Writes one JPEG per readable photo plus `manifest.tsv` (name, size, SHA-256),
//! and prints a table with sizes, quality and timing.

use std::path::{Path, PathBuf};
use std::process::ExitCode;
use std::time::Instant;

use photon_core::pipeline::{self, Preset};
use sha2::{Digest, Sha256};

fn main() -> ExitCode {
    match run(std::env::args().skip(1).collect()) {
        Ok(()) => ExitCode::SUCCESS,
        Err(msg) => {
            eprintln!("photon-cli: {msg}");
            eprintln!("usage: photon-cli [--preset small|standard|sharp] <input-dir> <output-dir>");
            ExitCode::FAILURE
        }
    }
}

fn run(args: Vec<String>) -> Result<(), String> {
    let mut preset = Preset::Standard;
    let mut dirs = Vec::new();
    let mut it = args.into_iter();
    while let Some(arg) = it.next() {
        match arg.as_str() {
            "--preset" | "-p" => {
                let name = it.next().ok_or("--preset needs a value")?;
                preset = Preset::from_name(&name).ok_or(format!("unknown preset '{name}'"))?;
            }
            "--help" | "-h" => return Err("help".into()),
            _ => dirs.push(PathBuf::from(arg)),
        }
    }
    let [input, output] = <[PathBuf; 2]>::try_from(dirs).map_err(|_| "need two folders")?;
    std::fs::create_dir_all(&output).map_err(|e| format!("{}: {e}", output.display()))?;

    let files = list_files(&input)?;
    let names: Vec<String> = files
        .iter()
        .map(|p| p.file_name().unwrap().to_string_lossy().into_owned())
        .collect();
    let out_names = pipeline::output_file_names(&names);
    let spec = preset.spec();
    println!(
        "preset {} ({} px, cap {} KB)",
        preset.name(),
        spec.target_pixels,
        spec.max_bytes / 1024
    );
    println!(
        "{:<28} {:>9} {:>11} {:>9} {:>6} {:>3} {:>8}",
        "file", "in KB", "out size", "out KB", "dist", "enc", "ms"
    );

    let (mut total_in, mut total_out, mut failed) = (0usize, 0usize, 0usize);
    let mut manifest = String::from("name\tbytes\twidth\theight\tdistance\tsha256\n");
    for (path, out_name) in files.iter().zip(&out_names) {
        let bytes = std::fs::read(path).map_err(|e| format!("{}: {e}", path.display()))?;
        let start = Instant::now();
        let result = pipeline::process(&bytes, preset);
        let ms = start.elapsed().as_secs_f64() * 1000.0;
        let name = path.file_name().unwrap().to_string_lossy();
        match result {
            Ok(p) => {
                total_in += bytes.len();
                total_out += p.jpeg.len();
                std::fs::write(output.join(out_name), &p.jpeg)
                    .map_err(|e| format!("{out_name}: {e}"))?;
                println!(
                    "{:<28} {:>9.1} {:>11} {:>9.1} {:>6.3} {:>3} {:>8.1}",
                    truncate(&name, 28),
                    bytes.len() as f64 / 1024.0,
                    format!("{}x{}", p.width, p.height),
                    p.jpeg.len() as f64 / 1024.0,
                    p.distance,
                    p.encodes,
                    ms
                );
                manifest.push_str(&format!(
                    "{out_name}\t{}\t{}\t{}\t{}\t{}\n",
                    p.jpeg.len(),
                    p.width,
                    p.height,
                    p.distance,
                    hex(&Sha256::digest(&p.jpeg))
                ));
            }
            Err(e) => {
                failed += 1;
                println!("{:<28} skipped: {e}", truncate(&name, 28));
            }
        }
    }
    std::fs::write(output.join("manifest.tsv"), manifest).map_err(|e| e.to_string())?;
    let done = files.len() - failed;
    println!(
        "{done} photos: {:.1} MB in, {:.1} MB out{}",
        total_in as f64 / 1_048_576.0,
        total_out as f64 / 1_048_576.0,
        if failed > 0 {
            format!(", {failed} skipped")
        } else {
            String::new()
        }
    );
    Ok(())
}

/// Regular files in the folder, sorted by name so runs are repeatable.
fn list_files(dir: &Path) -> Result<Vec<PathBuf>, String> {
    let mut files: Vec<PathBuf> = std::fs::read_dir(dir)
        .map_err(|e| format!("{}: {e}", dir.display()))?
        .filter_map(|e| e.ok().map(|e| e.path()))
        .filter(|p| p.is_file())
        .collect();
    files.sort();
    Ok(files)
}

fn truncate(s: &str, max: usize) -> String {
    if s.chars().count() <= max {
        s.to_string()
    } else {
        s.chars()
            .take(max - 1)
            .chain(std::iter::once('…'))
            .collect()
    }
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}
