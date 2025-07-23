#!/usr/bin/env python3
"""
Clean HTML to Markdown converter
Removes CSS classes, styling, and other HTML artifacts to produce clean markdown
"""

import re
import sys
from pathlib import Path
from bs4 import BeautifulSoup
import html2text

def clean_html_content(html_content):
    """Clean HTML content by removing unwanted elements and attributes"""
    soup = BeautifulSoup(html_content, 'html.parser')

    # Remove script, style, nav, footer, header elements
    for element in soup(["script", "style", "nav", "footer", "header", "aside"]):
        element.decompose()

    # Remove elements with specific classes that are navigation/UI
    for element in soup.find_all(attrs={"class": re.compile(r".*(nav|menu|sidebar|header|footer|breadcrumb).*", re.I)}):
        element.decompose()

    # Preserve code blocks by temporarily marking them
    code_blocks = []
    for pre in soup.find_all('pre'):
        placeholder = f"__CODE_BLOCK_{len(code_blocks)}__"
        code_blocks.append(pre.get_text())
        pre.replace_with(soup.new_string(placeholder))

    # Remove all attributes except href for links and src for images
    for tag in soup.find_all():
        attrs_to_keep = []
        if tag.name == 'a' and tag.has_attr('href'):
            attrs_to_keep.append('href')
        elif tag.name == 'img' and tag.has_attr('src'):
            attrs_to_keep.append('src')
        elif tag.name == 'img' and tag.has_attr('alt'):
            attrs_to_keep.append('alt')

        # Clear all attributes except the ones we want to keep
        tag.attrs = {k: v for k, v in tag.attrs.items() if k in attrs_to_keep}

    # Remove empty elements
    for tag in soup.find_all():
        if not tag.get_text(strip=True) and not tag.find_all(['img', 'br', 'hr']) and tag.name not in ['br', 'hr']:
            tag.decompose()

    # Convert divs to paragraphs where appropriate
    for div in soup.find_all('div'):
        if not div.find_all(['div', 'p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'pre', 'code', 'table']):
            div.name = 'p'

    # Get the cleaned HTML
    cleaned_html = str(soup)

    # Restore code blocks
    for i, code_block in enumerate(code_blocks):
        placeholder = f"__CODE_BLOCK_{i}__"
        cleaned_html = cleaned_html.replace(placeholder, f"<pre><code>{code_block}</code></pre>")

    return cleaned_html

def html_to_markdown(html_file, output_file):
    """Convert HTML file to clean markdown"""
    with open(html_file, 'r', encoding='utf-8') as f:
        html_content = f.read()

    # Clean the HTML first
    cleaned_html = clean_html_content(html_content)

    # Configure html2text for better markdown output
    h = html2text.HTML2Text()
    h.ignore_links = False
    h.ignore_images = False
    h.ignore_emphasis = False
    h.body_width = 0  # Don't wrap lines
    h.unicode_snob = True
    h.skip_internal_links = True
    h.ignore_tables = False

    # Convert to markdown
    markdown_content = h.handle(cleaned_html)

    # Post-process the markdown to clean it up further
    markdown_content = post_process_markdown(markdown_content)

    # Write to output file
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(markdown_content)

    print(f"Converted {html_file} -> {output_file}")

def post_process_markdown(content):
    """Post-process markdown to clean up common issues"""

    # Remove excessive blank lines
    content = re.sub(r'\n\s*\n\s*\n+', '\n\n', content)

    # Clean up list formatting
    content = re.sub(r'\n\s*\*\s*\n', '\n', content)

    # Remove standalone colons and other artifacts
    content = re.sub(r'\n\s*:\s*\n', '\n', content)

    # Clean up heading formatting
    content = re.sub(r'^#+\s*$', '', content, flags=re.MULTILINE)

    # Remove empty code blocks
    content = re.sub(r'```\s*```', '', content)

    # Clean up image references with data URIs
    content = re.sub(r'!\[\]\(data:image/[^)]+\)', '', content)

    # Remove excessive spaces in lines
    content = re.sub(r'[ \t]+', ' ', content)

    # Remove weird fragments and navigation elements
    content = re.sub(r'\n\s*[/\\]+\s*\n', '\n', content)
    content = re.sub(r'\n\s*objectdetection\s*\n', '\n', content)
    content = re.sub(r'\n\s*camera\s*\n', '\n', content)
    content = re.sub(r'\n\s*enums\s*\n', '\n', content)

    # Remove lines that are just single words that look like navigation
    content = re.sub(r'\n\s*\w+\s*\n(?=\s*\w+\s*\n)', '\n', content)

    # Clean up URLs that got broken
    content = re.sub(r'\]\s*\(\s*\)', ']', content)

    # Remove redundant "Updated:" lines that appear multiple times
    lines = content.split('\n')
    cleaned_lines = []
    seen_updated = False

    for line in lines:
        if re.match(r'^\s*Updated:\s*', line):
            if not seen_updated:
                cleaned_lines.append(line)
                seen_updated = True
        else:
            cleaned_lines.append(line)

    content = '\n'.join(cleaned_lines)

    # Clean up the start and end
    content = content.strip()

    return content

def main():
    if len(sys.argv) < 2:
        print("Usage: python html_to_clean_md.py <html_file> [output_file]")
        print("   or: python html_to_clean_md.py docs/*.html")
        sys.exit(1)

    # Handle multiple files or single file
    html_files = sys.argv[1:]

    for html_file in html_files:
        html_path = Path(html_file)
        if not html_path.exists():
            print(f"File not found: {html_file}")
            continue

        # Generate output filename
        if len(html_files) == 1 and len(sys.argv) == 3:
            output_file = sys.argv[2]
        else:
            output_file = html_path.with_suffix('.md')

        try:
            html_to_markdown(html_path, output_file)
        except Exception as e:
            print(f"Error converting {html_file}: {e}")

if __name__ == "__main__":
    main()