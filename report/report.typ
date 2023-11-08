#let project(title: "", authors: (), date: none, body) = {
  // Set the document's basic properties.
  set document(author: authors, title: title)
  set page(
    margin: (left: 27mm, right: 35mm, top: 30mm, bottom: 30mm),
    numbering: "1",
    number-align: center,
  )
  set text(font: "Linux Libertine", lang: "en")
  set heading(numbering: "1.1")

  // Title row.
  align(center)[
    #block(text(weight: 700, 1.75em, title))
    #v(1em, weak: true)
    #date
  ]

  // Author information.
  pad(
    top: 0.5em,
    bottom: 0.5em,
    x: 2em,
    grid(
      columns: (1fr,) * calc.min(3, authors.len()),
      gutter: 1em,
      ..authors.map(author => align(center, strong(author))),
    ),
  )

  // Main body.
  set par(justify: true)

  body
}

// The counter for questions
#let q_count = counter(<question>)

// Create the question layout with a grey box. It is inside a #locate
// because it needs to access the current top-level heading number.
#let question(wording, answer) = {
 locate(loc => {
	 let heading_level = counter(heading).at(loc).at(0)
	 let format_q_number(..nums) = {str(heading_level)+"-"+str(nums.pos().at(0))}
block(fill: luma(235),
	  inset: 10pt,
      radius: 6pt,
	  width: 100%,
	  [*Question<question> #q_count.display(format_q_number)*: _ #wording _\ #answer]
)
})
}

#let note(content) = {
 text(fill: red, [#text(font: "Unifont", size: 20pt, [⚠]) NOTE: ] + content)
	}

	#let title1_is_displayed() = {
 locate(loc => {
	 let heading_level = counter(heading).at(loc).at(0)
	 if heading_level == 0 {
	 }else [
		 #heading_level
	 ] + " "
})
}

// Reset the question counter for each top-level heading
#show heading.where(level: 1): it => {
 q_count.update(0)
	title1_is_displayed()
	it.body
	[\ ]
}

#set raw(tab-size: 4)
#show link:it => {underline(stroke: 1pt + navy, offset: 1.5pt, it)}

#show: project.with(
  title: "DevOps practical work",
  authors: (
    "Émile Royer",
  ),
  date: "November 2023",
)

#outline(depth: 1)

