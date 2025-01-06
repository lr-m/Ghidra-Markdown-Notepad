# Welcome! :)

## Introduction

This is a sample markdown document showing various formatting options. Markdown is a lightweight markup language that you can use to add formatting elements to plaintext text documents.

## Images

To import or paste an image, click the `Import Image` button, and paste/drag the image to the window, it will place the markdown in the current document cursor position.

![logo.png](images/logo.png)

## Memory Addresses

Click to jump to this address in the binary - saves having to copy addresses all the time...

[0x1c9954]

[34038b]

## Modification-Safe Function Names

In notes, instead of writing the function name you are currently using, you can instead reference the address, and the function name will be displayed as the current name in the preview. No more remembering function names you changed weeks ago!

*Note*: Will display the address if a function does not exist at the address - the below examples will not display the function as there probably isn't one at that exact location in your binary!

{0x1c9954}

{1c9954}

## Text Formatting

Here's how you can format text in different ways:
- **Bold text** is created using double asterisks
- *Italic text* uses single asterisks
- ***Bold and italic*** combines both

## Lists

### Unordered Lists

* First item
* Second item
    * Nested item
    * Another nested item
* Third item

### Ordered Lists

1. First step
2. Second step
    1. Sub-step one
    2. Sub-step two
    3. Third step

## Links

Started markdown link syntax that will open browser and redirect to web page when clicked:

[Visit my website](https://luke-m.xyz)

## Code

Inline code: `const greeting = "Hello, World!";`

Code block:

```python
def greet(name):
    print(f"Hello, {name}!")
    return True

greet("User")
```

## Quotes

> This is a blockquote. You can use it to emphasize or quote text.
>
> It can span multiple paragraphs if you add a > on the blank lines between them.

## Tables

| Header 1 | Header 2 | Header 3 |
|----------|----------|----------|
| Row 1    | Data     | Data     |
| Row 2    | Data     | Data     |




