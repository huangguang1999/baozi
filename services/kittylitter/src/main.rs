fn main() -> anyhow::Result<()> {
    alleycat::App {
        binary_name: "baozi",
        qualifier: "com",
        organization: "kris99",
        application: "baozicli",
        label: "com.kris99.baozicli",
        version: env!("CARGO_PKG_VERSION"),
    }
    .run()
}
