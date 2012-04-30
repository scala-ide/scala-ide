# run it with 'Rscript graph-it.R <filename>'

library(ggplot2)

args <- commandArgs(TRUE)
if (length(args) < 1) {
	stop("Please pass the filename with the memory data.")
}

filename <- args[1]
if (grepl("https://", filename)) {
	temporaryFile <- tempfile()
	download.file(filename,destfile=temporaryFile, method="curl")
	data <- read.table(temporaryFile)
} else {
	data <- read.table(filename)
}

y <- data$usedMem
n = length(y)

x <- 1:n
y1 = y[2:n]
x1 = 2:n

coef <- lm(y1~x1)
a <- coef$coefficients[1]
b <- coef$coefficients[2]

p <- qplot(x, y, main="Memory usage when repeatedly typing in Typers.scala", xlab="Run", ylab="Memory (MB)")
p + geom_line() + geom_abline(intercept=a, slope=b)
