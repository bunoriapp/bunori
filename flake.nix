{
    description = "studio environment setup";
    inputs = {
        nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    };
    outputs = { self, nixpkgs }:
        let
            system = "x86_64-linux";
            pkgs = import nixpkgs {
                inherit system;
                config = {
                    allowUnfree = true;
                    android_sdk.accept_license = true;
                };
            };
        in {
            devShells.${system}.default = pkgs.mkShell {
                packages = with pkgs; [
                    android-studio
                    android-tools
                    jdk17
                    cmake
                    gcc
                ];
                env.JAVA_HOME = "${pkgs.jdk17}";
                env.IN_NIX_DEVELOP = "true";
            };
        };
}
