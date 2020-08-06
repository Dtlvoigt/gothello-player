//author: Dylan Voigt
//cs441 final project: minimax gothello player

//this project uses code from Grossthello.java and Workboard.java
//https://github.com/pdx-cs-ai/gothello-grossthello

import java.lang.*;
import java.io.*;
import java.util.Vector;
import java.util.Random;

class TerminationException extends Exception {}

public class MyPlayer 
{
	GthClient myClient;
	Board myBoard = new Board();
	Move bestMove = null;
	int depth;
	static final int INF = 26;

	//constructor
	public MyPlayer(GthClient newClient, int depth) 
	{
		this.myClient = newClient;
		this.depth = depth;
	}
	
	//returns a score based on current board state
	int evaluation(Board board)
	{
		int checker1 = 0;
		int checker2 = 0;
		for(int i = 0; i < 5; i++)
			for(int j = 0; j < 5; j++)
				if(board.square[i][j] == board.checker_of(board.to_move))
					checker1++;
				else if(board.square[i][j] == board.checker_of(board.opponent(board.to_move)))
					checker2++;
		return checker1-checker2;
	}


	//finds best move for the current board state
	public int minimax(Board currBoard, int depth, boolean maximizer, boolean findMove) 
	{
		//generate all possible moves
		Vector<Move> allMoves = currBoard.genMoves();
		int numMoves = allMoves.size();

		//base cases
		if(numMoves == 0)
		{
			//best move is to pass
			bestMove = new Move();
			Board tempBoard = new Board(currBoard);
			int status = tempBoard.try_move(bestMove);

			//if you must pass, continue until game ends
			if(status != currBoard.GAME_OVER)
				return minimax(tempBoard, INF, !maximizer, false);

			//if game is over return high score for maximizer win and low for minimizer
			int result = tempBoard.referee();
			if(result == currBoard.to_move) 
			{
				if(maximizer)
					return INF;
				return -INF;
			}
			if(result == currBoard.opponent(currBoard.to_move)) 
			{
				if(maximizer)
					return -INF;
				return INF;
			}
			//game is draw
			return 0;
		}
		//max depth reached, return heuristic
		if(depth <= 0) 
		{
			if(maximizer)
				return evaluation(currBoard);
			return -evaluation(currBoard);
		}

		//if maximizers turn
		if(maximizer)
		{
			//try all moves out on temporary boards and save return values
			Vector<Integer> values = null;
			if(findMove)
				values = new Vector<Integer>(numMoves);
			int max = -INF;
			for(int i = 0; i < numMoves; i++)
			{
				Move move = allMoves.get(i);
				Board tempBoard = new Board(currBoard);

				//check if move is illegal or for game end state
				int status = tempBoard.try_move(move);
				if(status == currBoard.ILLEGAL_MOVE)
					throw new Error("unexpected illegal move");
				if(status == currBoard.GAME_OVER)
					throw new Error("unexpected game over");

				//call minimax for minimizers turn
				//if a better value is found update max
				int value = minimax(tempBoard, depth-1, !maximizer, false);
				if(findMove)
					values.add(i, new Integer(value));
				if(value >= max)
					max = value;
			}
			//unless original call, return here
			if(!findMove)
				return max;
		}

		//only minimizer access past this point
		//try all moves out on temporary boards and save return values
		Vector<Integer> values = null;
		if(findMove)
			values = new Vector<Integer>(numMoves);
		int min = INF;
		for(int i = 0; i < numMoves; i++)
		{
			Move move = allMoves.get(i);
			Board tempBoard = new Board(currBoard);

			//check if move is illegal or for game end state
			int status = tempBoard.try_move(move);
			if(status == currBoard.ILLEGAL_MOVE)
				throw new Error("unexpected illegal move");
			if(status == currBoard.GAME_OVER)
				throw new Error("unexpected game over");

			//call minimax for maximizers turn
			//if a better value is found update min
			int value = minimax(tempBoard, depth-1, !maximizer, false);
			if(findMove)
				values.add(i, new Integer(value));
			if(value <= min)
				min = value;
		}

		//unless original call, return here
		if(!findMove)
			return min;

		//find all moves that return the best possible score
		int best = 0;
		for(int i = 0; i < numMoves; i++)
			if(((Integer)(values.get(i))).intValue() == min)
				best++;

		//select one of the best moves at random and play it
		Random rand = new Random();
		int randomMove = rand.nextInt(best);
		for(int i = 0; i < numMoves; i++)
			if(((Integer)(values.get(i))).intValue() == min)
				if(randomMove-- == 0)
					bestMove = allMoves.get(i);
		return min;
	}

	//attempts to send best move to server as determined by my minimax algorithm
	private void make_my_move() throws TerminationException 
	{
		//find best move
		minimax(myBoard, depth, false, true);
		System.out.println("me:  " + myBoard.serial + ". " +
				bestMove.name());

		//check if I somehow made an illegal move
		int result = myBoard.try_move(bestMove);
		if (result == myBoard.ILLEGAL_MOVE)
			throw new Error("made illegal move somehow");

		//check if the server will accept my move or if game ends
		int state;
		try {
			state = myClient.make_move(bestMove);
		} catch(IOException e) {
			e.printStackTrace(System.out);
			throw new Error("move refused by referee"); }
		if (state == myClient.STATE_CONTINUE && result != myBoard.CONTINUE)
			throw new Error("client expected game over");
		if (state == myClient.STATE_DONE) {
			if (result != myBoard.GAME_OVER)
				System.out.println("unexpected game over");
			throw new TerminationException();
		}
	}

	//retrieves opponents move from the server
	private void get_opp_move() throws TerminationException 
	{
		//attempt to retrieve move
		int state;
		try {
			state = myClient.get_move();
		} catch(IOException e) {
			e.printStackTrace(System.out);
			throw new Error("couldn't receive move");
		}
		if (state == myClient.STATE_DONE)
			throw new TerminationException();
		System.out.println("opp: " + myBoard.serial + ". " +
				myClient.move.name());

		//make sure received move isn't illegal
		int result = myBoard.try_move(myClient.move);
		if (result == myBoard.ILLEGAL_MOVE)
			throw new Error("somehow received an illegal move");
	}

	//begins game, loops until game ends
	public void play() 
	{
		//determine whose turn it is
		try {
			while (true) {
				if (myClient.who == myClient.WHO_BLACK)
					make_my_move();
				else
					get_opp_move();
				if (myClient.who == myClient.WHO_WHITE)
					make_my_move();
				else
					get_opp_move();
			}
		//once exception is thrown, report winner or draw
		} catch(TerminationException e) {
			System.out.print("game ends with ");
			switch (myClient.winner) {
				case GthClient.WHO_WHITE:
					System.out.println("white win");
					break;
				case GthClient.WHO_BLACK:
					System.out.println("black win");
					break;
				case GthClient.WHO_NONE:
					System.out.println("draw");
					break;
			}
		}
	}

	//main
	public static void main(String args[])
		throws IOException {
			//check input args
			if (args.length != 4)
				throw new IllegalArgumentException(
						"usage: [black|white] <hostname> <server-number> <depth>");

			//determine which side you're playing
			int side;
			if (args[0].equals("black"))
				side = GthClient.WHO_BLACK;
			else if (args[0].equals("white"))
				side = GthClient.WHO_WHITE;
			else
				throw new IllegalArgumentException("unknown side");

			//determine server/host number
			String host = args[1];
			int server = Integer.parseInt(args[2]);

			//create client based on inputs and begin playing
			GthClient newClient = new GthClient(side, host, server);
			int depth = Integer.parseInt(args[3]);
			MyPlayer game = new MyPlayer(newClient, depth);

			game.play();
		}
}
