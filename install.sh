#!/bin/bash

# Download mod cli
curl --request GET \
--output mod.tar.gz \
--url 'https://api.app.moderne.io/cli/download?operatingSystem=linux-tar&environment=stable' \
--header 'Authorization: Bearer ************************************************'  # IGNORE

# Extract the archive
tar -xzf mod.tar.gz

# Make the binary executable
chmod +x "${PWD}/mod"

# Create .moderne directory in user's home
mkdir -p $HOME/.moderne/bin

# Copy mod to .moderne/bin directory
cp "${PWD}/mod" "$HOME/.moderne/bin/mod"

# Add .moderne/bin to PATH if not already present
if [[ $PATH != *".moderne/bin"* ]]; then
  echo "export PATH=\$PATH:\$HOME/.moderne/bin" >> $HOME/.bashrc
  echo "source <(mod generate-completion)" >> $HOME/.bashrc
  source $HOME/.bashrc
fi

# Clean up the downloaded files
rm mod.tar.gz mod

echo "mod CLI has been installed successfully!"
